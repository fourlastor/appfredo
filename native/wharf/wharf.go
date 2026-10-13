// Package wharf exposes a deliberately small Android API for signed, single-APK updates.
package wharf

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/itchio/headway/state"
	"github.com/itchio/lake/pools/fspool"
	"github.com/itchio/lake/tlc"
	"github.com/itchio/savior/filesource"
	_ "github.com/itchio/wharf/decompressors/brotli"
	_ "github.com/itchio/wharf/decompressors/gzip"
	"github.com/itchio/wharf/pwr/bowl"
	"github.com/itchio/wharf/pwr/patcher"
)

// Apply reconstructs an APK in a fresh private directory, checks its complete
// SHA-256, and only then moves it into outputApk. It never patches the base in place.
// A patch must describe exactly one ordinary file named app.apk on both sides.
func Apply(baseApk, patchFile, outputApk, fromHash, toHash string, expectedSize int64) (err error) {
	defer func() {
		if value := recover(); value != nil {
			err = fmt.Errorf("invalid Wharf patch: %v", value)
		}
	}()
	if expectedSize < 1 || expectedSize > 4<<30 {
		return fmt.Errorf("unsupported APK size")
	}
	if _, err = os.Lstat(outputApk); !os.IsNotExist(err) {
		return fmt.Errorf("output must not exist")
	}
	if err = verify(baseApk, fromHash); err != nil {
		return fmt.Errorf("base APK: %w", err)
	}
	baseInfo, err := os.Stat(baseApk)
	if err != nil {
		return err
	}
	work, err := os.MkdirTemp(filepath.Dir(outputApk), ".wharf-")
	if err != nil {
		return err
	}
	defer os.RemoveAll(work)
	target := filepath.Join(work, "base")
	output := filepath.Join(work, "new")
	if err = os.Mkdir(target, 0700); err != nil {
		return err
	}
	if err = os.Link(baseApk, filepath.Join(target, "app.apk")); err != nil {
		if err = copyFile(baseApk, filepath.Join(target, "app.apk")); err != nil {
			return err
		}
	}
	source, err := filesource.Open(patchFile)
	if err != nil {
		return err
	}
	defer source.Close()
	p, err := patcher.New(source, &state.Consumer{})
	if err != nil {
		return err
	}
	if err = singleAPK(p.GetTargetContainer(), baseInfo.Size()); err != nil {
		return err
	}
	if err = singleAPK(p.GetSourceContainer(), expectedSize); err != nil {
		return err
	}
	pool := fspool.New(p.GetTargetContainer(), target)
	defer pool.Close()
	b, err := bowl.NewFreshBowl(bowl.FreshBowlParams{
		TargetContainer: p.GetTargetContainer(), SourceContainer: p.GetSourceContainer(),
		TargetPool: pool, OutputFolder: output,
	})
	if err != nil {
		return err
	}
	defer b.Close()
	if err = p.Resume(nil, pool, &boundedBowl{Bowl: b, limit: expectedSize}); err != nil {
		return err
	}
	if err = b.Commit(); err != nil {
		return err
	}
	result := filepath.Join(output, "app.apk")
	info, err := os.Stat(result)
	if err != nil {
		return err
	}
	if info.Size() != expectedSize {
		return fmt.Errorf("reconstructed APK size mismatch")
	}
	if err = verify(result, toHash); err != nil {
		return fmt.Errorf("reconstructed APK: %w", err)
	}
	return os.Rename(result, outputApk)
}

func singleAPK(c *tlc.Container, size int64) error {
	if len(c.Files) != 1 || len(c.Dirs) != 0 || len(c.Symlinks) != 0 ||
		c.Files[0].Path != "app.apk" || c.Files[0].Size != size || c.Size != size || c.Files[0].Offset != 0 {
		return fmt.Errorf("patch must contain only app.apk with the declared size")
	}
	return nil
}

type boundedBowl struct {
	bowl.Bowl
	limit int64
}

func (b *boundedBowl) GetWriter(index int64) (bowl.EntryWriter, error) {
	w, err := b.Bowl.GetWriter(index)
	if err != nil {
		return nil, err
	}
	return &boundedWriter{EntryWriter: w, remaining: b.limit}, nil
}

type boundedWriter struct {
	bowl.EntryWriter
	remaining int64
}

func (w *boundedWriter) Write(p []byte) (int, error) {
	if int64(len(p)) > w.remaining {
		return 0, fmt.Errorf("patch exceeds declared output size")
	}
	n, err := w.EntryWriter.Write(p)
	w.remaining -= int64(n)
	return n, err
}

func verify(path, expected string) error {
	if len(expected) != 64 {
		return fmt.Errorf("invalid SHA-256")
	}
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()
	h := sha256.New()
	if _, err = io.Copy(h, f); err != nil {
		return err
	}
	if hex.EncodeToString(h.Sum(nil)) != expected {
		return fmt.Errorf("SHA-256 mismatch")
	}
	return nil
}
func copyFile(from, to string) error {
	source, err := os.Open(from)
	if err != nil {
		return err
	}
	defer source.Close()
	out, err := os.OpenFile(to, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(out, source)
	closeErr := out.Close()
	if copyErr != nil {
		return copyErr
	}
	return closeErr
}

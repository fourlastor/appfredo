package wharf

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"os"
	"path/filepath"
	"testing"

	"github.com/itchio/lake/tlc"
)

func hashBytes(value []byte) string { h := sha256.Sum256(value); return hex.EncodeToString(h[:]) }
func TestRealButlerPatch(t *testing.T) {
	old, err := os.ReadFile("testdata/old/app.apk")
	if err != nil {
		t.Fatal(err)
	}
	next, err := os.ReadFile("testdata/new/app.apk")
	if err != nil {
		t.Fatal(err)
	}
	output := filepath.Join(t.TempDir(), "app.apk")
	if err = Apply("testdata/old/app.apk", "testdata/update.pwr", output, hashBytes(old), hashBytes(next), int64(len(next))); err != nil {
		t.Fatal(err)
	}
	actual, _ := os.ReadFile(output)
	if !bytes.Equal(actual, next) {
		t.Fatal("reconstruction differs from new APK")
	}
	unchanged, _ := os.ReadFile("testdata/old/app.apk")
	if !bytes.Equal(old, unchanged) {
		t.Fatal("base was modified")
	}
}
func TestRejectsInvalidInputWithoutPublishingOutput(t *testing.T) {
	old, _ := os.ReadFile("testdata/old/app.apk")
	next, _ := os.ReadFile("testdata/new/app.apk")
	for _, test := range []struct {
		name, from, to string
		size           int64
	}{
		{"wrong base", hashBytes([]byte("wrong")), hashBytes(next), int64(len(next))},
		{"wrong result", hashBytes(old), hashBytes([]byte("wrong")), int64(len(next))},
		{"wrong size", hashBytes(old), hashBytes(next), int64(len(next) + 1)},
	} {
		t.Run(test.name, func(t *testing.T) {
			output := filepath.Join(t.TempDir(), "app.apk")
			if Apply("testdata/old/app.apk", "testdata/update.pwr", output, test.from, test.to, test.size) == nil {
				t.Fatal("invalid patch accepted")
			}
			if _, err := os.Stat(output); !os.IsNotExist(err) {
				t.Fatal("invalid result was published")
			}
		})
	}
}
func TestRejectsUnexpectedContainerPaths(t *testing.T) {
	for _, path := range []string{"../app.apk", "/app.apk", "other.apk", "sub/app.apk"} {
		container := &tlc.Container{Size: 10, Files: []*tlc.File{{Path: path, Size: 10}}}
		if singleAPK(container, 10) == nil {
			t.Fatalf("accepted %s", path)
		}
	}
}
func TestExistingOutputPreserved(t *testing.T) {
	output := filepath.Join(t.TempDir(), "app.apk")
	os.WriteFile(output, []byte("keep"), 0600)
	if Apply("testdata/old/app.apk", "testdata/update.pwr", output, "", "", 1) == nil {
		t.Fatal("existing output accepted")
	}
	got, _ := os.ReadFile(output)
	if string(got) != "keep" {
		t.Fatal("existing output was modified")
	}
}

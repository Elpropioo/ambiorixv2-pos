import JSZip from 'jszip';
import { saveAs } from 'file-saver';

// Import all Android project files as raw text using Vite's glob import
const projectRawFiles = import.meta.glob(
  [
    '/settings.gradle.kts',
    '/build.gradle.kts',
    '/gradle.properties',
    '/gradlew',
    '/gradlew.bat',
    '/gradle/wrapper/gradle-wrapper.properties',
    '/README.md',
    '/COMO_GENERAR_APK.md',
    '/.github/workflows/build-apk.yml',
    '/app/build.gradle.kts',
    '/app/proguard-rules.pro',
    '/app/src/main/AndroidManifest.xml',
    '/app/src/main/res/values/*.xml',
    '/app/src/main/res/xml/*.xml',
    '/app/src/main/res/drawable/*.xml',
    '/app/src/main/res/mipmap-anydpi-v26/*.xml',
    '/app/src/main/java/com/ambiorix/pay/**/*.kt',
    '/app/src/test/java/com/ambiorix/pay/*.kt'
  ],
  { query: '?raw', eager: true, import: 'default' }
);

export async function triggerClientSideZipDownload() {
  try {
    const res = await fetch('/AmbiorixPOS-Android-Project.zip');
    if (res.ok) {
      const blob = await res.blob();
      saveAs(blob, 'AmbiorixPOS-Android-Project.zip');
      return true;
    }
  } catch (e) {
    console.warn('Direct fetch failed, using JSZip fallback', e);
  }

  try {
    const zip = new JSZip();

    for (const [key, content] of Object.entries(projectRawFiles)) {
      // Remover slash inicial para estructura relativa correcta en el zip
      const relativePath = key.replace(/^\//, '');
      if (typeof content === 'string') {
        zip.file(relativePath, content);
      }
    }

    const blob = await zip.generateAsync({
      type: 'blob',
      compression: 'DEFLATE',
      compressionOptions: { level: 9 }
    });

    saveAs(blob, 'AmbiorixPOS-Android-Project.zip');
    return true;
  } catch (err) {
    console.error('Error generating client-side zip:', err);
    // Fallback al endpoint del servidor
    window.location.href = '/AmbiorixPay-Android-Project.zip';
    return false;
  }
}

# Doc A4 Print & Crop 📄🖨️

A professional Android application built with **Kotlin** and **Jetpack Compose** designed for scanning, auto-detecting edges, manually adjusting corners, and arranging front and back sides of ID cards, passports, and documents onto a single A4 page for clean printing and PDF export.

---

## ✨ Key Features

- **📷 Front & Back Capture**: Capture or import front and back sides of documents (ID cards, driver's licenses, credit cards, certificates).
- **🤖 Auto-Detect Document Edges**: Intelligent image processing algorithm using standard Android Bitmap APIs to automatically detect document boundaries and crop out backgrounds.
- **🖐️ Manual 4-Corner Adjustment**: Interactive custom `Canvas` component allowing users to drag and adjust all 4 document corners (Top-Left, Top-Right, Bottom-Right, Bottom-Left) for precise perspective correction.
- **⚡ Auto-Capture Manager**: Automatic photo capture trigger when document edges are stable and properly framed.
- **🎨 Advanced Filters & Layouts**:
  - **Layout Styles**: Stacked or Side-by-Side arrangements on standard A4 page layout.
  - **Print Filters**: Vivid Color Print or B&W Laser Scan / High Contrast mode.
  - **Cut Marks & Borders**: Optional dashed/solid cut guides for easy physical cutting.
- **🖨️ High-Resolution PDF & Print**: Instant PDF generation rendered precisely to A4 page dimensions for direct printer sharing or storage.
- **💾 Local Persistence**: Robust offline storage using **Room Database**.

---

## 🛠️ Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) & Material Design 3 (M3)
- **Architecture**: MVVM (Model-View-ViewModel) + Kotlin Coroutines & Flow
- **Database**: Room Database (SQLite)
- **Build System**: Gradle (Kotlin DSL)
- **CI/CD**: GitHub Actions workflow for automated APK and Android App Bundle (AAB) builds and GitHub Releases.

---

## 🚀 CI/CD & Automated Releases

This repository includes a fully configured GitHub Actions workflow (`.github/workflows/build-apk.yml`) that automatically:
1. Generates temporary signing keystores.
2. Builds **Debug APK**, **Debug AAB**, and **Release AAB** (`gradle :app:bundleRelease`).
3. Uploads build artifacts (`app-debug-apk`, `app-debug-aab`, `app-release-aab`).
4. Automatically creates a GitHub Release and attaches all binary distribution assets.

---

## 👨‍💻 Developer

Developed by **Azazmadkiya**  
🌐 [azazmadkiya.morbi.store](https://azazmadkiya.morbi.store)

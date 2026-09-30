# MuhgoubHUD

تطبيق أندرويد للوحة تحكم عائمة (System Quick Toggles & Connectivity Dashboard).

## طريقة رفعه على GitHub وبناء الـ APK تلقائياً (بدون Android Studio)

1. روح على github.com وسجّل دخول، ثم اعمل **New repository** (مثلاً باسم `MuhgoubHUD`)، خليه **Public** أو **Private** حسب رغبتك، وما تضيفش README تلقائي.
2. فك ضغط هذا المجلد على جهازك، وارفع كل محتوياته (بما فيها مجلد `.github`) إلى الريبو، إما:
   - عن طريق سحب الملفات في واجهة GitHub (زر **Add file → Upload files**)، أو
   - عن طريق الأوامر:
     ```bash
     cd MuhgoubHUD
     git init
     git add .
     git commit -m "Initial commit"
     git branch -M main
     git remote add origin https://github.com/USERNAME/MuhgoubHUD.git
     git push -u origin main
     ```
3. بمجرد الرفع، GitHub Actions (الموجود في `.github/workflows/build.yml`) هيشتغل تلقائياً ويبني التطبيق.
4. عشان تشوف النتيجة وتنزّل الـ APK:
   - افتح الريبو على GitHub
   - روح لتبويب **Actions**
   - افتح آخر تشغيل (Run) باسم "Build APK"
   - لما يخلص (علامة صح خضراء ✅)، هتلاقي أسفل الصفحة قسم **Artifacts** فيه ملف اسمه `MuhgoubHUD-debug-apk` — نزّله (هيجيلك كملف zip يحتوي على الـ APK جوه)
5. انقل الـ APK لموبايلك وثبّته (لازم تفعّل "السماح بالتثبيت من مصادر غير معروفة" لو طلب منك أندرويد ذلك).

## ملاحظة
هذا الـ APK ناتج عن `assembleDebug`، وهو مناسب للتجربة والاستخدام الشخصي مباشرة. لو حبيت لاحقاً تنشره على متجر أو توقّعه بمفتاح رسمي (Release build)، محتاج خطوة إضافية لعمل Signing Key.

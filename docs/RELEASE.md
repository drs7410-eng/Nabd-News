# البناء والتوقيع والتحديث

الـ APK المسلّم موقّع بتوقيع تطوير ويصلح للتجربة الشخصية. لم يُنشر على متجر أو خادم خارجي. Android 8+ مدعوم، والهدف SDK 35؛ يحتاج Galaxy S26 Ultra اختبارًا فعليًا للشاشة ولوحة المفاتيح وNano.

للتحديث فوق تثبيت سابق يلزم توقيع APK بالمفتاح نفسه وزيادة versionCode. توقيع GitHub Actions الافتراضي للتطوير قد يتغير بين البيئات، فلا تعتمد عليه لتحديثات تحفظ البيانات. قبل تغيير التوقيع صدّر نسخة JSON؛ حذف التطبيق يحذف قاعدة البيانات والخزنة المحلية.

لإنشاء توقيع شخصي ثابت للإصدارات التالية، ولّد keystore خارج المشروع واحتفظ به بأمان:

```bash
keytool -genkeypair -v -keystore /your/private/path/nabd-release.jks -alias nabd -keyalg RSA -keysize 3072 -validity 10000
./gradlew assembleRelease
# استبدل مسار build-tools ومسار keystore محليًا؛ أدخل كلمات المرور تفاعليًا.
$ANDROID_HOME/build-tools/35.0.0/zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/nabd-aligned.apk
$ANDROID_HOME/build-tools/35.0.0/apksigner sign --ks /your/private/path/nabd-release.jks --out Nabd-release.apk /tmp/nabd-aligned.apk
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose Nabd-release.apk
```

لا تُدرج كلمات مرور أو مسارات خاصة في Gradle أو Git. لا يحتاج توقيع التطبيق مفاتيح AI. لإصدار ثابت عبر Actions أضف إعداد توقيع خاصًا ومراجَعًا باستخدام GitHub Secrets؛ لم يُدرج أي سر في هذا المشروع.

النقل إلى خادم وسيط لاحقًا يكون بإضافة `AiProvider` جديد يعيد `AiResult`، واستبدال الحقن داخل `NabdViewModel`، مع إلغاء حفظ مفاتيح الخدمة على العميل. بقية الدفتر والتحليلات وRoom والواجهات تستخدم الواجهة نفسها.

# Translation SDK and attribution

Nabd World uses Google Translate through the **on-device ML Kit Translation API**, not a newly enabled paid Cloud Translation endpoint. It also optionally uses the already-configured OpenAI/Gemini account, only on explicit user action and subject to source permissions.

Official SDK documentation: https://developers.google.com/ml-kit/language/translation/android
Usage/branding: https://developers.google.com/ml-kit/language/translation/translation-terms
Attribution requirements: https://docs.cloud.google.com/translate/attribution
Google Translate: https://translate.google.com/

The unmodified attribution bitmap `app/src/main/res/drawable-nodpi/google_translate_attribution.png` comes from Google's official sample:
https://github.com/googlesamples/mlkit/blob/f1047837fe1e02d58063bfc30b830fad23ffbc35/android/translate-showcase/app/src/main/res/drawable/greyscale_regular_3x.png

Google sample code is under Apache License 2.0. Google trademarks remain Google's property and are used for required translation attribution subject to its branding rules. No affiliation or endorsement of Nabd or its editorial outputs is implied. The bitmap is not a news image or a publisher logo.

ML Kit machine translation, especially via English as an intermediary, is not a guarantee of editorial Arabic quality. Numeric/quote checks and glossary protection are conservative structural guards; semantic and journalistic review is still required. No real model inference or human Arabic quality approval has been completed in the Cloud environment.

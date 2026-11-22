# Minhad privacy policy (draft)

> **Notes for the owner. Delete this box before publishing.**
>
> - This is a draft written on 22 November 2026 from a reading of the code at version 0.24.1
>   (`docs/RELEASE_AUDIT.md` gives the place of every fact). It describes what the app does **today**. If the code
>   changes before release (for example: background location removed, another map provider, backup rules, a link
>   changed), the matching paragraph here must change with it. Paragraphs that depend on such a decision are marked
>   **[CHECK]**.
> - Three things are yours to fill in, marked like `[THIS]`: the publisher's name, a contact e-mail address, and
>   the date. No address has been invented.
> - Google Play needs this text at a public web address (a page anyone can open without signing in), entered in
>   the Play Console, and reachable from inside the app.
> - It is not legal advice. If you publish in countries with their own privacy laws and want certainty, have a
>   lawyer read it.
> - English first, then the same in Arabic.

---

## English

**Minhad (منهاد) — Privacy policy**

Effective: `[DATE]` · Publisher: `[PUBLISHER NAME]` · Contact: `[CONTACT EMAIL]`

Minhad is a prayer-time app for Android phones and Wear OS watches. This policy says what the app does with
information about you. It covers the phone app and the watch app.

### The short version

- Minhad has **no accounts, no advertising, no analytics and no tracking**. It contains no advertising or
  analytics software.
- **We, the publisher, receive nothing.** We run no server. Nothing you do in Minhad is sent to us.
- Prayer times, the Qibla, the calendar and the alerts are **calculated on your device** and work without the
  internet.
- Your location is used to calculate prayer times and the Qibla for where you are. It is kept on your device. In a
  few cases, listed below, it or something close to it is sent to a service that is not ours (your phone's own
  place-name service, a map service, your own watch, your own device backup). We list every one.

### Information Minhad uses

**Your location.** If you allow it, Minhad asks your phone for one position when it needs one (when you open the
app, when you ask, when your time zone changes). It does not follow you continuously. You choose precise or
approximate location in Android's own prompt; Minhad works with either. You can refuse and choose a city, a point
on the map or coordinates instead.

**[CHECK] Location in the background.** Only if you turn on "Update in the background" and choose "Allow all the
time", Minhad takes one low-power position about every six hours while the app is closed, so that prayer times
stay right when you travel. This is off unless you turn it on, and you can turn it off at any time.

**Your settings.** The place you chose, your calculation method and adjustments, language, alert choices, widget
choices, watch-face designs and similar preferences.

**Sounds you add.** If you import an audio file to use as an alert, Minhad keeps a copy inside the app.

**Alert history.** A list of the last 40 alerts on the device and what happened to each (played, stopped, late), so
that you can see whether the Adhan sounded.

**Tasbih counts** (watch). Kept on the watch.

Minhad does not ask for your name, e-mail address, phone number, contacts, photos, microphone or camera, and does
not read any device or advertising identifier.

### Where it is kept

All of the above is stored **on your device**, in the app's private storage. Map pictures you have viewed are kept
in the app's temporary storage for up to 30 days (about 150 MB at most) so the map opens faster.

**[CHECK] Device backup.** If Android's backup is turned on for your device, Android may include Minhad's settings
and files, among them your saved place and last position and any sounds you imported, in the backup it keeps in
your own Google account, and may restore them on a new device. That backup is run by Android and Google under
your Google account's settings, not by us. The alert history and the map pictures are not included.

### When information leaves your device

Minhad itself connects to the internet in three situations only. Every connection is encrypted (HTTPS). As with any
internet connection, the service you connect to sees your device's IP address.

1. **[CHECK] The map.** When you open "Choose on the map" or the page that confirms a place, the map pictures for
   the area on screen are fetched from Esri (ArcGIS Online, `server.arcgisonline.com`). The request says which map
   squares are needed, which shows Esri the area you are looking at (usually where you are, or the place you are
   choosing), together with the app's name and version. Nothing is fetched when the map is not on screen.
2. **Searching for a place on a phone that has no built-in place-name service.** The words you type are sent to
   Photon, a public search service run by komoot that uses OpenStreetMap data (`photon.komoot.io`), together with
   the app's name and version. Your position is not sent. On most phones this is never used, because the phone's
   own service (below) is used instead.
3. **The Singapore timetable.** If your prayer times follow MUIS (Singapore), Minhad downloads the public MUIS
   timetable from Singapore's open-data site (`data.gov.sg`) at most once a week. The request contains nothing
   about you beyond what such a request always carries: your IP address and Android's standard description of the
   device (its Android version and model).

Minhad also uses services that are part of your phone, which may send information to their own provider:

4. **Your phone's place-name service (Android's "Geocoder"; on most phones it is provided by Google).** To show the
   name of where you are ("Al Karama, Dubai"), Minhad gives your position to this service when your position has
   changed by more than about 300 metres (precise location) or 3 kilometres (approximate), and when you place a pin
   on the map or enter coordinates. When you search for a place by name, the words you type are given to it. How
   that service handles the request is governed by its provider's privacy policy (for Google, Google's).
5. **Your phone's location service** (Google Play services on most phones) provides the position. Whether it sends
   anything to its provider depends on your phone's own location settings, not on Minhad.

Between your own devices:

6. **Your watch.** If you use Minhad on a Wear OS watch paired with your phone, the phone sends the watch what it
   needs to show the same times: your position and place name, time zone, calculation settings, alert choices, the
   coming weeks' prayer times, your chosen watch face or design, and, if you chose them for alerts on the watch,
   Adhan recordings or sounds you imported. The watch tells the phone its app version, its Wear OS version, the
   state of the watch face, whether notifications and exact alarms are allowed, and which sounds it holds. This
   travels over Wear OS's own connection between your paired devices (provided by Google Play services). If you set
   the watch up on the watch itself with "Use my location", that position stays on the watch.

Only when you choose to:

7. **"Share report"** (Adhan → Alert history) prepares a plain text containing Minhad's version, your phone's
   maker, model and Android version, your alert settings and the alert history, and hands it to the app you pick
   (for example e-mail or a messaging app). It contains no position and no place name. Nothing is sent unless you
   send it.
8. **Links.** Some buttons open a web page or the Play Store in your browser or store app (for example the source
   and licence of a recording, or the watch's set-up guide). What happens there is covered by those sites' own
   policies.

We do not sell information, and we do not share it with anyone for advertising, because we never hold it.

### Permissions, and how to take them back

| Permission | Why Minhad asks | Without it |
|---|---|---|
| Location (precise or approximate) | Prayer times and the Qibla for where you are | Choose your place by hand |
| **[CHECK]** Location "all the time" | The optional refresh while the app is closed | Times update when you open the app |
| Notifications | Prayer alerts with a Stop button, reminders, the countdown notification | No alerts can be shown or heard |
| Alarms & reminders (exact alarms) | The Adhan at the exact minute | Alerts may be late |
| Full-screen notifications | The Adhan screen over the lock screen | A normal notification with Stop |
| Run a foreground service | Playing the Adhan; keeping the countdown notification ticking | Granted with installation |
| Run at start-up, vibrate, keep the device awake while playing | Re-arming alerts after a restart; the alert itself | Granted with installation |
| Internet | The three connections listed above | Granted with installation; everything else works offline |
| On the watch: add and show Minhad's watch face | The watch faces Minhad offers | You are asked on the watch before the face is shown |

You can change any permission at any time: Android **Settings → Apps → Minhad → Permissions** (on the watch:
**Settings → Apps → Minhad → Permissions**). In Minhad, Settings → Location lets you switch to a place chosen by
hand, and "Update in the background" can be switched off there.

### Keeping and deleting

Everything is on your device, so you are in control of it:

- Sounds you imported can be deleted in Adhan → My sounds.
- **Settings → Apps → Minhad → Storage → Clear storage** erases everything Minhad has stored on that device;
  uninstalling the app does the same.
- A backup in your Google account, if you have one, is managed in your Google account's backup settings.

Because we hold nothing about you, there is nothing for us to delete, correct or hand over. If you have a question
all the same, write to us.

### Children

Minhad is a general-audience app and is not directed at children under 13. We do not knowingly collect personal
information from anyone, children included, since the app sends nothing to us.

### Changes

If the app changes in a way that affects this policy, we will update this page and its date before the change is
released.

### Contact

`[PUBLISHER NAME]` · `[CONTACT EMAIL]`

---

## العربية

**منهاد (Minhad) — سياسة الخصوصية**

تاريخ السريان: `[التاريخ]` · الناشر: `[اسم الناشر]` · للتواصل: `[البريد الإلكتروني للتواصل]`

منهاد تطبيق لمواقيت الصلاة لهواتف أندرويد وساعات Wear OS. تبيّن هذه السياسة ما يفعله التطبيق بالمعلومات المتعلقة
بك، وهي تشمل تطبيق الهاتف وتطبيق الساعة.

### الخلاصة

- ليس في منهاد **حسابات ولا إعلانات ولا تحليلات ولا تتبّع**، ولا يحتوي على أي برمجيات إعلانية أو تحليلية.
- **نحن، الناشر، لا يصلنا شيء.** لا نشغّل أي خادم، ولا يُرسَل إلينا شيء مما تفعله في منهاد.
- مواقيت الصلاة والقبلة والتقويم والتنبيهات **تُحسب على جهازك** وتعمل من دون إنترنت.
- يُستخدم موقعك لحساب مواقيت الصلاة واتجاه القبلة للمكان الذي أنت فيه، ويُحفظ على جهازك. وفي حالات قليلة مذكورة
  أدناه يُرسَل الموقع، أو ما يدلّ عليه، إلى خدمة ليست لنا (خدمة أسماء الأماكن في هاتفك، خدمة الخرائط، ساعتك،
  النسخة الاحتياطية لجهازك). وقد ذكرناها كلها.

### المعلومات التي يستخدمها منهاد

**موقعك.** إذا سمحت بذلك، يطلب منهاد من هاتفك موقعًا واحدًا عند الحاجة (عند فتح التطبيق، أو عند طلبك، أو عند تغيّر
المنطقة الزمنية). لا يتتبّعك باستمرار. تختار أنت الموقع الدقيق أو التقريبي في نافذة أندرويد نفسها، ومنهاد يعمل
بأيٍّ منهما. ويمكنك أن ترفض وتختار مدينة أو نقطة على الخريطة أو إحداثيات بدلًا من ذلك.

**[للمراجعة] الموقع في الخلفية.** فقط إذا فعّلت «التحديث في الخلفية» واخترت «السماح طوال الوقت»، يأخذ منهاد موقعًا
واحدًا منخفض استهلاك الطاقة كل ست ساعات تقريبًا والتطبيق مغلق، لتبقى المواقيت صحيحة أثناء السفر. هذا الخيار
متوقف ما لم تفعّله، ويمكنك إيقافه في أي وقت.

**إعداداتك.** المكان الذي اخترته، وطريقة الحساب والتعديلات، واللغة، وخيارات التنبيهات والأدوات (الويدجت)
وتصاميم واجهة الساعة وما شابهها.

**الأصوات التي تضيفها.** إذا استوردت ملفًا صوتيًا لاستخدامه تنبيهًا، يحتفظ منهاد بنسخة منه داخل التطبيق.

**سجلّ التنبيهات.** قائمة بآخر 40 تنبيهًا على الجهاز وما جرى لكلٍّ منها (شُغّل، أُوقف، تأخّر)، لتعرف هل رُفع الأذان.

**عدّاد التسبيح** (الساعة). يبقى على الساعة.

لا يطلب منهاد اسمك ولا بريدك الإلكتروني ولا رقم هاتفك ولا جهات اتصالك ولا صورك ولا الميكروفون ولا الكاميرا، ولا
يقرأ أي معرّف للجهاز أو للإعلانات.

### أين تُحفظ

كل ما سبق محفوظ **على جهازك**، في مساحة التخزين الخاصة بالتطبيق. وتُحفظ صور الخرائط التي شاهدتها في التخزين المؤقت
للتطبيق مدة أقصاها 30 يومًا (نحو 150 ميغابايت على الأكثر) ليُفتح عرض الخريطة أسرع.

**[للمراجعة] النسخ الاحتياطي للجهاز.** إذا كان النسخ الاحتياطي في أندرويد مفعّلًا على جهازك، فقد يضمّ أندرويد إعدادات
منهاد وملفاته، ومنها المكان المحفوظ وآخر موقع وأي أصوات استوردتها، إلى النسخة الاحتياطية التي يحفظها في حسابك
أنت على Google، وقد يستعيدها على جهاز جديد. هذا النسخ يجريه أندرويد وGoogle بحسب إعدادات حسابك، لا نحن. ولا يشمل
سجلّ التنبيهات ولا صور الخرائط.

### متى تغادر المعلومات جهازك

يتصل منهاد نفسه بالإنترنت في ثلاث حالات فقط، وكل اتصال مشفَّر (HTTPS). وكما في أي اتصال بالإنترنت، ترى الخدمة التي
تتصل بها عنوان IP لجهازك.

1. **[للمراجعة] الخريطة.** عند فتح «الاختيار من الخريطة» أو صفحة تأكيد المكان، تُجلب صور الخريطة للمنطقة المعروضة من
   شركة Esri (خدمة ArcGIS Online، ‏`server.arcgisonline.com`). يذكر الطلب مربعات الخريطة المطلوبة، وهذا يُظهر لـ Esri
   المنطقة التي تنظر إليها (وهي غالبًا مكانك أو المكان الذي تختاره)، مع اسم التطبيق وإصداره. ولا يُجلب شيء ما دامت
   الخريطة غير معروضة.
2. **البحث عن مكان في هاتف ليست فيه خدمة لأسماء الأماكن.** تُرسَل الكلمات التي تكتبها إلى Photon، وهي خدمة بحث عامة
   تديرها komoot وتعتمد على بيانات OpenStreetMap ‏(`photon.komoot.io`)، مع اسم التطبيق وإصداره. لا يُرسَل موقعك. وفي
   معظم الهواتف لا تُستخدم هذه الخدمة أبدًا، لأن خدمة الهاتف نفسها (أدناه) تُستخدم بدلًا منها.
3. **جدول سنغافورة.** إذا كانت مواقيتك على جدول المجلس الإسلامي في سنغافورة (MUIS)، ينزّل منهاد الجدول العام من موقع
   البيانات المفتوحة في سنغافورة (`data.gov.sg`) مرة في الأسبوع على الأكثر. لا يتضمن الطلب شيئًا عنك سوى ما يحمله أي طلب من هذا النوع: عنوان IP، ووصف أندرويد
   المعتاد للجهاز (إصدار أندرويد وطراز الهاتف).

ويستخدم منهاد كذلك خدمات هي جزء من هاتفك، وقد ترسل معلومات إلى مزوّدها:

4. **خدمة أسماء الأماكن في هاتفك (‏Geocoder في أندرويد، ومزوّدها في معظم الهواتف Google).** ليعرض منهاد اسم مكانك
   («الكرامة، دبي») يعطي موقعك لهذه الخدمة كلما تغيّر موقعك أكثر من 300 متر تقريبًا (الموقع الدقيق) أو 3 كيلومترات
   (التقريبي)، وكذلك عندما تضع دبوسًا على الخريطة أو تُدخل إحداثيات. وعند البحث عن مكان باسمه تُعطى الكلمات التي
   تكتبها لهذه الخدمة. وتعامُل الخدمة مع الطلب تحكمه سياسة الخصوصية لمزوّدها (سياسة Google إن كانت هي المزوّد).
5. **خدمة الموقع في هاتفك** (خدمات Google Play في معظم الهواتف) هي التي تعطي الموقع. وإرسالها شيئًا إلى مزوّدها
   يتوقف على إعدادات الموقع في هاتفك، لا على منهاد.

بين أجهزتك أنت:

6. **ساعتك.** إذا استخدمت منهاد على ساعة Wear OS مقترنة بهاتفك، يرسل الهاتف إلى الساعة ما تحتاج إليه لعرض المواقيت
   نفسها: موقعك واسم المكان، والمنطقة الزمنية، وإعدادات الحساب، وخيارات التنبيهات، ومواقيت الأسابيع القادمة،
   وواجهة الساعة أو التصميم الذي اخترته، وكذلك تسجيلات الأذان أو الأصوات التي استوردتها إذا اخترتها لتنبيهات
   الساعة. وتخبر الساعةُ الهاتفَ بإصدار تطبيقها وإصدار Wear OS وحالة واجهة الساعة، وهل الإشعارات والمنبّهات
   الدقيقة مسموحة، وأي الأصوات لديها. يجري ذلك عبر اتصال Wear OS نفسه بين جهازيك المقترنين (توفّره خدمات
   Google Play). وإذا أعددت الساعة من الساعة نفسها بخيار «استخدام موقعي» فذلك الموقع يبقى على الساعة.

وفقط عندما تختار أنت:

7. **«مشاركة التقرير»** (الأذان ← سجل التنبيهات) يجهّز نصًّا عاديًّا فيه إصدار منهاد، وصانع هاتفك وطرازه وإصدار
   أندرويد، وإعدادات التنبيهات وسجلّها، ويسلّمه إلى التطبيق الذي تختاره (البريد أو تطبيق مراسلة مثلًا). لا يتضمن
   موقعًا ولا اسم مكان، ولا يُرسَل شيء إلا إذا أرسلته أنت.
8. **الروابط.** بعض الأزرار تفتح صفحة ويب أو متجر Play في متصفحك أو تطبيق المتجر (مثل مصدر تسجيلٍ ورخصته، أو دليل
   إعداد الساعة). وما يجري هناك تحكمه سياسات تلك المواقع.

لا نبيع المعلومات ولا نشاركها مع أحد لأغراض الإعلان، لأنها لا تصل إلينا أصلًا.

### الأذونات وكيف تسحبها

| الإذن | لماذا يطلبه منهاد | من دونه |
|---|---|---|
| الموقع (الدقيق أو التقريبي) | مواقيت الصلاة والقبلة لمكانك | تختار مكانك يدويًّا |
| **[للمراجعة]** الموقع «طوال الوقت» | التحديث الاختياري والتطبيق مغلق | تُحدَّث المواقيت عند فتح التطبيق |
| الإشعارات | تنبيهات الصلاة مع زر الإيقاف، والتذكيرات، وإشعار العدّ التنازلي | لا يظهر أي تنبيه ولا يُسمع |
| المنبّهات والتذكيرات (المنبّهات الدقيقة) | الأذان في دقيقته تمامًا | قد تتأخر التنبيهات |
| إشعارات ملء الشاشة | شاشة الأذان فوق شاشة القفل | إشعار عادي فيه زر الإيقاف |
| تشغيل خدمة في الواجهة | تشغيل الأذان، وإبقاء إشعار العدّ التنازلي يعمل | يُمنح عند التثبيت |
| التشغيل عند بدء الجهاز، والاهتزاز، وإبقاء الجهاز مستيقظًا أثناء التشغيل | إعادة ضبط التنبيهات بعد إعادة التشغيل، والتنبيه نفسه | يُمنح عند التثبيت |
| الإنترنت | الاتصالات الثلاثة المذكورة أعلاه | يُمنح عند التثبيت، وكل ما عداها يعمل دون اتصال |
| على الساعة: إضافة واجهة منهاد وعرضها | واجهات الساعة التي يقدّمها منهاد | تُسأل على الساعة قبل عرض الواجهة |

يمكنك تغيير أي إذن في أي وقت: **الإعدادات ← التطبيقات ← منهاد ← الأذونات** في أندرويد (وعلى الساعة: **الإعدادات ←
التطبيقات ← منهاد ← الأذونات**). وفي منهاد نفسه: الإعدادات ← الموقع، حيث يمكنك اختيار المكان يدويًّا وإيقاف
«التحديث في الخلفية».

### الحفظ والحذف

كل شيء على جهازك، فالأمر بيدك:

- الأصوات التي استوردتها تُحذف من: الأذان ← أصواتي.
- **الإعدادات ← التطبيقات ← منهاد ← مساحة التخزين ← محو البيانات** يمحو كل ما خزّنه منهاد على ذلك الجهاز، وكذلك
  إلغاء تثبيت التطبيق.
- النسخة الاحتياطية في حسابك على Google، إن وُجدت، تُدار من إعدادات النسخ الاحتياطي في حسابك.

ولأننا لا نحتفظ بأي شيء عنك، فليس عندنا ما نحذفه أو نصحّحه أو نسلّمه. وإن كان لديك سؤال مع ذلك فراسلنا.

### الأطفال

منهاد تطبيق لعموم المستخدمين وليس موجَّهًا إلى الأطفال دون 13 سنة. ولا نجمع عن علمٍ معلومات شخصية من أحد، ومنهم
الأطفال، لأن التطبيق لا يرسل إلينا شيئًا.

### التغييرات

إذا تغيّر التطبيق بما يمسّ هذه السياسة، فسنحدّث هذه الصفحة وتاريخها قبل إصدار ذلك التغيير.

### للتواصل

`[اسم الناشر]` · `[البريد الإلكتروني للتواصل]`

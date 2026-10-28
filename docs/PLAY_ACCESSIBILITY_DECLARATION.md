# Muhafız — Google Play Accessibility declaration notes

## Feature name

Android 14+ gelişmiş güvenli ekran doğrulama.

## Why AccessibilityService is used

Muhafız riskli görsel içerik algıladığında kullanıcıya opak bir koruma perdesi gösterir.
Normal MediaProjection bu perde açıkken alttaki uygulama penceresini göremez.
Android 14 ve üzerindeki desteklenen cihazlarda AccessibilityService,
`takeScreenshotOfWindow()` ile yalnız ilgili application window'u koruma perdesini
kaldırmadan doğrulamak için kullanılır.

Bu kullanımın amacı, kullanıcı riskli sayfadan sistem geri hareketiyle çıktığında
alttaki sayfanın gerçekten güvenli olduğunu cihaz üzerinde doğrulamak ve ancak o
zaman koruma perdesini kaldırmaktır.

## Accessibility data accessed

Muhafız yalnız koruma aktifken ve engelleme ekranı gösterilirken:

- ilgili window/content değişiklik olaylarını dinler;
- görünür application window kimliğini ve paket bilgisini değerlendirir;
- Android 14+ üzerinde ilgili pencerenin screenshot'unu anlık olarak alır.

System UI, Muhafız'ın kendi pencereleri ve accessibility overlay temiz içerik kanıtı
olarak kullanılmaz.

## Local-only processing

Pencere görüntüleri:

- yalnız cihaz belleğinde TensorFlow Lite modeliyle analiz edilir;
- diske yazılmaz;
- cache'e kaydedilmez;
- analytics'e eklenmez;
- crash report'a eklenmez;
- sunucuya gönderilmez;
- üçüncü taraflarla paylaşılmaz.

## What Muhafız does NOT do with Accessibility

Muhafız AccessibilityService üzerinden:

- kullanıcı adına tıklama yapmaz;
- gesture dispatch etmez;
- global Back/Home/Recents eylemi çağırmaz;
- metin yazmaz;
- mesaj göndermez;
- uygulamalarda otomatik gezinmez;
- kullanıcı ayarlarını değiştirmez.

Muhafız bir disability accessibility tool değildir ve
`isAccessibilityTool=true` beyan etmez.

## Prominent disclosure

Koruma başlatılırken, Android 14+ cihazda Accessibility etkin ve daha önce açık
kullanıcı onayı verilmiş değilse ayrı bir **tam ekran uygulama içi onay ekranı**
gösterilir. Bu ekran ekran-yakalama açıklamasından ayrıdır ve Accessibility
ayarları açılmadan hemen önce görünür.

Ekran açıkça şunları belirtir:

- AccessibilityService API'nin, engellenen içeriğin arkasındaki uygulama
  penceresinin güvenli hale gelip gelmediğini doğrulamak için kullanıldığı;
- koruma sırasında pencere/içerik değişikliği olayları ile görünür uygulama
  penceresinin kimliği/paket bilgisinin değerlendirilebildiği;
- Android 14+ desteklenen cihazlarda ilgili uygulama penceresinin anlık
  görüntüsünün yalnız doğrulama amacıyla işlenebildiği;
- görüntülerin yalnız cihaz belleğinde anlık işlendiği, diske/cache'e
  kaydedilmediği, sunucuya gönderilmediği ve üçüncü taraflarla paylaşılmadığı;
- kullanıcı adına tıklama, gesture, metin girişi, mesaj gönderme, Back/Home
  işlemi veya otomatik gezinme yapılmadığı;
- özelliğin isteğe bağlı olduğu ve reddedildiğinde güvenli manuel geri dönüş
  akışının kullanılabildiği.

Olumlu buton:
**Kabul ediyorum ve Erişilebilirlik Ayarlarını Aç**

Alternatif:
**Erişilebilirlik olmadan devam et**

Kullanıcının geri tuşuyla ekrandan çıkması veya ekranı terk etmesi onay sayılmaz.
Olumlu onay yalnız açıkça olumlu butona basıldığında kaydedilir.

Kullanıcı reddederse koruma tamamen kapanmaz; güvenli manuel geri dönüş modu kullanılır.

## Reviewer flow

1. Uygulamayı açın ve reviewer/developer erişimini etkinleştirin.
2. Koruma başlatma akışını açın.
3. Ekran analizi disclosure'ını kabul edin.
4. Android 14+ cihazda ayrı tam ekran "Erişilebilirlik İzni ve Veri Kullanımı"
   disclosure ekranını görüntüleyin.
5. Ekranın erişilen veri, kullanım amacı, yerel işleme/paylaşmama ve
   kullanıcı adına otomasyon yapılmadığı açıklamalarını gösterdiğini doğrulayın.
6. "Kabul ediyorum ve Erişilebilirlik Ayarlarını Aç" düğmesine dokunun.
7. Sistem Accessibility ayarlarında Muhafız hizmetini etkinleştirin.
8. Uygulamaya dönün ve MediaProjection iznini verin.
9. Riskli test içeriğinde koruma perdesinin açıldığını doğrulayın.
10. Sistem geri hareketiyle güvenli önceki sayfaya dönün.
11. Koruma perdesi görünür kalırken pencere doğrulamasının gerçekleştiğini ve yalnız
    yeterli temiz kanıttan sonra perdenin otomatik kalktığını doğrulayın.

## Android 11–13 fallback

API 30–33 üzerinde `takeScreenshotOfWindow()` mevcut değildir.
Muhafız otomatik underlay doğrulaması iddia etmez.

Kullanıcıya:

1. sistem geri hareketiyle riskli sayfadan çıkması,
2. ardından "Muhafız’a dön" düğmesine dokunması

söylenir. Koruma perdesi bu manuel akış sırasında fail-closed kalır.

## Secure window / unsupported verification

Android 14+ üzerinde hedef pencere `FLAG_SECURE` kullanıyorsa veya screenshot API
güvenilir bir görüntü sağlayamıyorsa otomatik doğrulama yapılmaz.
Koruma perdesi açık kalır ve kullanıcı manuel fallback akışına yönlendirilir.

## Suggested Play Console declaration text

"Muhafız, ebeveyn kontrolü kapsamında riskli görsel içerik engellendikten sonra
koruma perdesinin arkasındaki uygulama penceresinin artık güvenli olup olmadığını
doğrulamak için AccessibilityService API kullanır. Android 14+ üzerinde
Accessibility window screenshot yalnız cihaz üzerinde TensorFlow Lite modeliyle
anlık analiz edilir; görüntüler kaydedilmez, sunucuya gönderilmez veya üçüncü
taraflarla paylaşılmaz. Hizmet kullanıcı adına tıklama, metin girişi, mesaj
gönderme, gesture dispatch veya otomatik gezinme gerçekleştirmez. Kullanıcıdan
özellik etkinleştirilmeden önce ayrı prominent disclosure ve affirmative consent
alınır."

## Review video checklist

Video şunları tek akışta göstermelidir:

- uygulamanın açılması;
- ayrı tam ekran Accessibility disclosure metninin tamamı;
- "Kabul ediyorum ve Erişilebilirlik Ayarlarını Aç" ile olumlu kullanıcı onayı;
- sistem Accessibility ayarında Muhafız'ın açılması;
- riskli içerikte block ekranı;
- kullanıcı geri hareketi;
- Android 14+ otomatik güvenli pencere doğrulaması;
- ayrıca Accessibility izni verilmediğinde disclosure'ın yeniden ulaşılabilir ve
  manuel fallback'in çalışır olduğu akış.

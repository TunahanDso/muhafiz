# Overlay release kararı — 2026-10-08

İncelenen başlangıç: `release/final-hardening`,
`935fd57405856648c2746be73243f32f77527ab7`. Main'e merge edilmedi.

**NOT READY TO SHIP.** Bu değişiklik güvenlik düzeltmesidir; otomatik
underlying-content doğrulama implementasyonu değildir. B senaryosu çözülmedi.
Build'in başarılı olması bu ürün gereksiniminin karşılandığı anlamına gelmez.

## Doğrulanmış hatalar ve uygulanan değişiklik

- **BLOCKER, strings.xml / block_safe_action:** ASCII apostrof AAPT2 için
  kaçışlanmamıştı. Önce `Muhafız\'a dön` yapıldı ve
  `:app:mergeLabDebugResources` BUILD SUCCESSFUL ile doğrulandı. Sonraki
  değişiklikte iki overlay butonu ve kullanılmayan action stringleri kaldırıldı.
- **HIGH, OverlayService.requestUnderlyingScreenRecheck ve
  ScreenCaptureService.onStartCommand:** recheck komutu görüntüyü doğrulamadan
  `hideOverlay(force = true)` ve `resetDetectionState()` çalıştırıyordu.
  Aynı riskli sayfa yeniden algılanana kadar görünürdü. Buton, komut işleyicisi
  ve action sabiti tamamen kaldırıldı; periyodik veya tek karelik açma yok.
- **HIGH, ScreenCaptureService.handleIncomingFrame / handleDetectionResult:**
  eski fail-closed kontrolü yalnız `isSuspiciousBlankFrame()` true olduğunda
  çalışıyordu. Siyah zemin üzerindeki UI veya System UI kareleri için bu garanti
  yoktu. Overlay açıkken tüm display-capture kareleri artık sınıflandırmadan
  bırakılıyor; yolda olan bir NONE sonucu da perdeyi indiremez. Overlay state'i
  reader/main thread arasında görünürlük için volatile yapıldı. Rotation kontrolü
  kare reddinden önce kalır; ikinci VirtualDisplay oluşturulmaz.

İstenen 14 detection sabiti başlangıç commit'i ile karşılaştırıldı: değişmedi.
Billing, entitlement, WorkManager, projection-token kullanımı, START_NOT_STICKY,
generation guard ve ImageFrameReader teardown kodu değiştirilmedi.

## Neden mevcut mimari kilitleniyor?

Tam ekran projection, kullanıcının gördüğü birleşik görüntüyü yakalar.
Opak application overlay alttaki uygulamanın piksel bilgisini bu görüntüden
silmiştir. Classifier'a daha fazla süre vermek veya clean-frame eşiğini düşürmek
eksik bilgiyi geri getirmez. Bu nedenle güvenli sayfaya geri dönmek ile riskli
sayfada kalmak aynı yakalama görüntüsünü üretebilir.

`FLAG_SECURE` bir capture-exclusion/underlay API'si değildir: pencerenin
yakalanmasını engeller, alttaki pikselleri isteme hakkı vermez. Transparan
pencere hem kullanıcıya hem capture'a içerik sızdırır. 1–2 frame perdeyi
kaldırmak da ekranda gerçek gösterimdir; VSync/SurfaceFlinger ile üçüncü taraf
uygulamalar için atomik "sadece analiz et, kullanıcıya gösterme" anlaşması yoktur.
Back, uygulama değişikliği, pencere başlığı, UsageStats veya zaman aşımı
"içerik değişmiş olabilir" sinyalidir; görsel güvenlik kanıtı değildir.

Mevcut patch'te `MONITORING -> BLOCKED` risk kararıyla gerçekleşir.
`BLOCKED + display frame -> BLOCKED`: siyah, yazılı veya System UI olması fark etmez.
Kullanıcının notification üzerinden Muhafız'ı açması için mevcut
`ACTION_SAFE_SCREEN_VISIBLE` yolu korunmuştur. Bu, Chrome sayfasını değiştirmez
ve alttaki içeriğin doğrulandığı anlamına gelmez. Stop/lock yolları ayrı lifecycle
işlemleridir; token yeniden kullanılmaz. Bu yollar için aşağıdaki cihaz testleri
hala gereklidir; ekranda hiç geçiş sızıntısı olmadığı iddia edilmez.

## Mimari karar ve gerçek alternatifler

Mevcut **Android 11–16, tüm uygulamalar, gizli/secure pencereler dahil,
flash olmadan otomatik geri dönüş** kapsamını public MediaProjection ve
application-overlay API'leriyle birlikte yerine getiren bir çözüm bulunmuyor.
Bu nedenle yeni bir flag veya kör re-sampling hilesi production'a eklenmedi.

Üçüncü taraf uygulamalarda bu UX'in uygulanabilir yönü Android 14+ için
**pencere bazlı capture + accessibility overlay** mimarisidir:

1. Açık kullanıcı onayı ve etkin AccessibilityService üzerinden
   `takeScreenshotOfWindow(windowId, executor, callback)` kullanılır.
   `TYPE_ACCESSIBILITY_OVERLAY`, örtülü pencerelerin introspection'ını korur.
2. Navigation/window olayları sadece yeni örnekleme başlatır. Perde kapanmaz.
   `BLOCKED -> VERIFYING_UNDERLAY` geçişinde window ID, session generation,
   window generation, görüntü zamanı/boyutu ve görünürlük doğrulanır.
3. Bütün ilgili görünür application pencereleri hesaba katılır; split-screen,
   PiP veya dialog varken yalnız aktif pencerenin temiz olması yeterli değildir.
4. Yalnız aynı pencere nesline ait bağımsız ve güncel görüntüler normal
   classifier geçmişini ilerletebilir. Risk/yeni pencere/hata/secure/timeout
   `BLOCKED` durumunu korur; eski callback'ler buffer'larını kapatıp atılır.
5. Temiz kanıt ve mevcut minimum görünürlük/hold koşulları tamamlanınca
   `VERIFYING_UNDERLAY -> MONITORING` olur. 7000 ms minimum süre korunurken
   yeni bir engelin bir saniyede açılacağı vaat edilemez.

Bu **tasarımdır; bu patch'te uygulanmış değildir**. Android 11–13'te pencere
screenshot API'si yoktur. Android 14+ üzerinde de secure window API hatası
döndürür; güvenli içerik ile riskli içerik ayırt edilemez. Chrome gizli sekmenin
secure olup olmadığı sürüm/ayar/cihazda doğrulanmalıdır; siyah frame NSFW
kanıtı değildir. Mevcut 10 saniyelik blank-frame heuristic'i bunu ayıramaz.
Bu nedenle Accessibility eklemek tek başına istenen bütün kapsamı çözmez.

Android 14+ tek uygulama MediaProjection paylaşımı ayrı bir alternatiftir:
kullanıcının seçtiği task yakalanır, tüm cihaz korunmaz; uygulama değişiminde
kapsam otomatik genişletilemez. Bu yüzden sessizce cihaz korumasının yerine
konmadı. Kendi browser/render alanında analiz ile örtü ayrı tutulabilir;
ancak bu da Chrome ve diğer uygulamaları koruyan mevcut ürünün yerine geçmez.

Release için öneri: mevcut kapsamla gönderimi durdurmak; üçüncü taraf koruması
devam edecekse Android 14+ bağımsız pencere kaynağını cihazda kanıtlamak ve
secure/legacy kapsamını açıkça sınırlamak. Android 11–13 + secure içerikte aynı
garantiyi vaat eden bir production çözümü yoktur. Kullanıcıdan bu kapsam
değişikliğini saklayan bir implementasyon yapılmamalıdır.

## Google Play etkisi

Bu patch production manifest'ine AccessibilityService veya yeni yetki eklemez.
Önerilen mimari uygulanırsa uygulama engelli erişimine yönelik olmadığı için
`isAccessibilityTool=true` kullanılamaz. Ayrı prominent disclosure, olumlu onay,
Play Console beyanı ve inceleme videosu gerekir. Pencere görüntülerinin yerel
işlenmesi ve saklanmaması açıkça anlatılmalı; screenshot yetkisiyle kullanıcı
adına gezinme/tıklama yapılmamalıdır. Approval garanti değildir; bu yeni izin
release için gerçek bir policy değerlendirmesi gerektirir.

## Cihaz smoke-test kabul planı

ADB listesinde cihaz/emülatör yoktu; aşağıdakiler çalıştırılmış test sonuçları
değildir. APK'nın derlenmesi instrumentation'ın cihazda koştuğu anlamına gelmez.
API 30, 33, 34, 35 QPR1+, 36; Pixel ve en az bir OEM cihazda tekrarlanmalı.
Harici kamera kaydı kullanın: uygulamanın kendi projection kaydı görünür flash'ı
ve secure pencereleri güvenilir biçimde kanıtlayamaz.

| Senaryo | Kabul koşulu / mevcut durum |
|---|---|
| A: risk -> block -> bekle | 60+ saniye bekle; perde kendiliğinden kalkmamalı. Patch kare geri beslemesini engeller; cihaz kanıtı bekliyor. |
| B: risk -> block -> back -> güvenli Chrome | Güncel bağımsız güvenli frame ile otomatik açılmalı. **Mevcut mimaride karşılanmıyor; release blocker.** |
| C: içerik değişmeden bekle | Bildirim panelini aç/kapat; siyah olmayan System UI veya overlay yazıları temiz kanıt sayılmamalı. |
| D: notification -> Muhafız | App açılmalı, projection oturumu korunmalı; ilk çizim/geri dönüş sırasında sızıntı ayrıca kamera ile kontrol edilmeli. |
| E: block -> rotation | Her iki yönde 10 tekrar; ikinci createVirtualDisplay/SecurityException yok, perde kaybolmuyor. |
| F: sistem projection stop | UI pasif, kaynaklar bırakılmış; otomatik token replay yok. |
| G: kilitle/aç | 15 QPR1+ projection stop sonrası pasif; eski sistemlerde gerçek oturum durumu ve ilk unlock frame'i kontrol edilmeli. |
| H: inference sırasında stop | Model çalışırken durdur; geç sonuç overlay/service'i yeniden açmamalı. |
| I: recents tek swipe | OS izin veriyorsa servis sürmeli. Clear all/process kill ayrı test; yeniden başlatmada yeni kullanıcı izni gerekli. |

## Kaynaklar

- [MediaProjection, capture kapsamı ve lifecycle](https://developer.android.com/media/grow/media-projection)
- [FLAG_SECURE](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE)
- [AccessibilityService, takeScreenshotOfWindow ve secure hatası](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [AccessibilityWindowInfo, overlay introspection](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo)
- [AOSP CTS, secure-window screenshot testi](https://android.googlesource.com/platform/cts/+/master/tests/accessibilityservice/src/android/accessibilityservice/cts/AccessibilityTakeScreenshotTest.java)
- [Google Play Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491)

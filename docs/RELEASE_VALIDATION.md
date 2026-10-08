# Release validation — 2026-10-08

Başlangıç branch/HEAD: `release/final-hardening` /
`935fd57405856648c2746be73243f32f77527ab7`.

İlk resource düzeltmesinden sonra `:app:mergeLabDebugResources`:
**BUILD SUCCESSFUL in 2m 38s**, 3 görev çalıştı.

Son uygulama kaynakları üzerinde, istenen Windows komutunun Linux eşdeğeri:

```sh
bash ./gradlew :app:testLabDebugUnitTest :app:assembleLabDebug :app:assembleLabDebugAndroidTest :app:lintProductionRelease :app:bundleProductionRelease --no-daemon
```

**BUILD SUCCESSFUL in 5m 15s**

`128 actionable tasks: 113 executed, 12 from cache, 3 up-to-date`

JDK: Temurin 17.0.20.1. Gradle wrapper: 8.13. SDK: android-36.
Build ortamı için Android SDK/JDK yolları, mevcut ağ proxy'si ve sistem CA
trust store'u ortamda tanımlandı; repo build ayarları değiştirilmedi.

- `testLabDebugUnitTest`: başarılı; mevcut tek test `addition_isCorrect`.
  Bu, overlay regression testi değildir.
- `assembleLabDebug`: başarılı.
- `assembleLabDebugAndroidTest`: başarılı; APK yalnız derlendi, cihazda koşulmadı.
- `lintProductionRelease`: başarılı. Mevcut uyarılar ayrı lint raporunda.
- `bundleProductionRelease`: başarılı. Bu sonuç Play yüklemesi, signing-key
  doğrulaması veya Play onayı anlamına gelmez.
- ADB cihaz listesi boş; A–I runtime senaryoları çalıştırılmadı.
- İstenen 14 detection sabiti başlangıç commit'i ile birebir aynı.
- `git diff --check`: başarılı.

Kaynaklar için yeni bir otomatik overlay testi eklenmedi; mevcut testlerin
runtime kapsamı olmadığı açıkça belirtiliyor. Cihazda capture/compositor,
window lifecycle ve secure-window davranışı ayrıca doğrulanmalı.

Ürün kararı: **NOT READY TO SHIP**. B senaryosu çözülmedi.
Gerekçe, değişen dosyalar, state geçişleri, API/policy değerlendirmesi ve A–I
smoke-test planı: [OVERLAY_RELEASE_DECISION.md](OVERLAY_RELEASE_DECISION.md).

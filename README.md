# 🛡️ Muhafız

**Muhafız**, uygunsuz görsel içeriklere karşı gerçek zamanlı ekran koruma sistemi olarak geliştirilmiş bir Android uygulamasıdır.

Uygulama, cihaz ekranında görüntülenen içerikleri kullanıcı izniyle analiz eder ve riskli içerik tespit edildiğinde ekranı siyah koruma katmanı ile gizler.

> Amaç: Daha güvenli, kontrollü ve ebeveyn odaklı bir dijital deneyim sunmak.

---

## 🚀 Temel Özellikler

- Gerçek zamanlı ekran içerik analizi
- Uygunsuz içerik algılandığında tam ekran gizleme
- Ekran üzerinde koruma katmanı
- Ebeveyn PIN kontrolü
- Arka planda çalışma desteği
- Google Play abonelik altyapısı
- Cihaz üzerinde yerel analiz yaklaşımı

---

## 🧠 Sistem Yaklaşımı

Muhafız, klasik filtreleme mantığından farklı olarak yalnızca belirli uygulamaları engellemeye odaklanmaz.

Bunun yerine cihaz ekranında görüntülenen içeriği analiz ederek, uygulama bağımsız bir koruma katmanı oluşturmayı hedefler.

Bu yaklaşım sayesinde sistem; sosyal medya, tarayıcı veya farklı uygulamalarda karşılaşılabilecek uygunsuz görsellere karşı daha esnek bir koruma modeli sunar.

---

## 🏗️ Teknik Mimari

Uygulama Kotlin tabanlı modern Android mimarisiyle geliştirilmiştir.

Kullanılan temel teknolojiler:

- Kotlin
- Jetpack Compose
- Android Foreground Service
- MediaProjection API
- Overlay Window System
- TensorFlow Lite
- Google Play Billing
- DataStore Preferences

---

## 🔐 İzinler

Muhafız’ın temel işlevlerini yerine getirebilmesi için bazı Android izinlerine ihtiyaç vardır:

- **MediaProjection:** Ekrandaki içeriğin analiz edilebilmesi için kullanılır.
- **SYSTEM_ALERT_WINDOW:** Riskli içerik algılandığında ekran üzerinde koruma katmanı oluşturmak için kullanılır.
- **POST_NOTIFICATIONS:** Arka planda çalışan koruma servisinin kullanıcıya görünür olması için kullanılır.

Tüm analiz işlemleri cihaz üzerinde gerçekleştirilir. Ekran verileri kaydedilmez, dışarı aktarılmaz veya üçüncü taraflarla paylaşılmaz.

---

## 💳 Abonelik Sistemi

Muhafız, Google Play Billing altyapısı ile aylık abonelik modelini destekler.

---

Abonelik işlemleri Google Play tarafından yönetilir. Uygulama ödeme bilgisi toplamaz veya saklamaz.

---

## 📦 Paket Bilgisi

**Play Store ürün adı:**  
Muhafız

**Kod tarafındaki package/namespace:**  
com.tunix.nazar

**Play Store applicationId:**  
com.tunix.muhafiz

---

## 🔒 Gizlilik Politikası

Gizlilik politikası GitHub Pages üzerinden yayınlanmaktadır:  
https://tunahandso.github.io/muhafiz/

---

## ⚠️ Not

Bu proje, ebeveyn kontrolü ve güvenli ekran deneyimi amacıyla geliştirilmiştir.

Android güvenlik politikaları gereği, cihaz yeniden başlatıldığında ekran yakalama izninin kullanıcı tarafından tekrar verilmesi gerekebilir.

---

## 👤 Geliştirici

Tunahan Delisalihoğlu

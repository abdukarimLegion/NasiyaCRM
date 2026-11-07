# Nasiya CRM

Murobaha asosidagi nasiya savdo platformasi. Hozir bitta mijozning serverida ishlaydi,
kod esa keyinchalik SaaS'ga o'tkazishga tayyorlab yozilgan.

```
nasiya CRM/
├── backend/            Java 21 + Spring Boot 3.5 + Gradle (Kotlin DSL) + PostgreSQL 16 + Flyway
├── frontend/           React 19 + TypeScript + Vite (prototip dizayni)
├── deploy/             zaxira nusxa olish va tiklash skriptlari
├── .github/workflows/  CI (build + test) va deploy (ghcr.io + SSH)
├── docker-compose.yml  db + backend + frontend(nginx) + backup (lokal build)
├── docker-compose.prod.yml  serverda: ghcr.io dan tayyor image'lar
└── .env.example        sozlamalar namunasi
```

## 1. Mijoz serverida ishga tushirish (Docker)

Serverda faqat **Docker** va **Docker Compose** bo'lishi kerak.

```bash
cp .env.example .env
nano .env                      # DB_PASSWORD, APP_JWT_SECRET, APP_ADMIN_PASSWORD ni o'zgartiring
docker compose up -d --build
docker compose logs -f backend # "Started InstallmentApplication" chiqishini kuting
```

Brauzerda `http://SERVER_IP` manzilini oching va `.env` dagi admin login/paroli bilan kiring.
Keyin **Sozlamalar** bo'limida kompaniya nomi, brend, rang, shartnoma prefiksi va
xodimlarni kiriting.

Demo ma'lumotlar bilan sinab ko'rish uchun `.env` da `SPRING_PROFILES_ACTIVE=dev` qo'ying
(3 ta filial va demo xodimlar: `operator1..3`, `kassir1`, `undiruvchi1`, paroli `demo12345`).
Mijoz serverida bu qatorni bo'sh qoldiring.

**HTTPS:** eng oddiy yo'l — serverda Caddy yoki Nginx + Let's Encrypt o'rnatib,
`localhost:80` ga proksilash. Tizim ichki tarmoqda ishlasa, `HTTP_PORT` yetarli.

### Zaxira nusxa

`backup` konteyneri har kuni soat 02:00 da `./backups/` papkasiga `pg_dump` oladi va
14 kundan eski nusxalarni o'chiradi. **Bu papkani boshqa diskka yoki bulutga ham
nusxalab turing.**

```bash
./deploy/restore.sh backups/nasiya_2026-10-01_0200.dump   # tiklash
```

### Yangilash

```bash
git pull   # yoki yangi fayllarni ko'chiring
docker compose up -d --build
```

Baza migratsiyalari (Flyway) backend ishga tushganda avtomatik bajariladi.

## 1.1. CI/CD (GitHub Actions) — VPS ga avtomatik deploy

| Workflow | Qachon | Nima qiladi |
|---|---|---|
| `.github/workflows/ci.yml` | PR (`master`/`main`) va deploy ichida | backend `./gradlew build` (testlar bilan), frontend `lint` + `tsc` + `vite build` |
| `.github/workflows/deploy.yml` | **`master` (yoki `main`) ga har push**, yoki qo'lda (*Run workflow*) | CI → image'larni `ghcr.io` ga yuklaydi → VPS ga SSH orqali chiqib yangilaydi → backend sog'lomligini tekshiradi |

**Serverdagi mavjud servislar va portlarga tegilmaydi** (`deploy/remote-deploy.sh`):

- faqat `nasiya` compose loyihasi yangilanadi (`nasiya-*` konteynerlar, `nasiya_default` tarmoq,
  `nasiya_pgdata` volume); `docker compose down`, `system prune`, `--remove-orphans` ishlatilmaydi;
- tashqariga faqat frontend ochiladi, standart port **8090** (80 emas); Postgres va backend portlari
  umuman ochilmaydi;
- deploy'dan oldin `HTTP_PORT` tekshiriladi: boshqa dastur yoki konteyner band qilgan bo'lsa,
  **hech narsa to'xtatilmaydi** — deploy xato bilan to'xtaydi va portni kim ushlab turgani ko'rsatiladi;
- `ghcr.io` login vaqtinchalik docker config'da qilinadi, serverdagi `~/.docker/config.json` o'zgarmaydi;
- faqat shu loyihaning eski image'lari o'chiriladi (oxirgi 3 tasi qoladi).

**Repo secret'lari** (Settings → Secrets and variables → Actions). SSH parol bilan ulanadi (`sshpass`):

| Secret | Izoh |
|---|---|
| `VPS_HOST` | VPS IP yoki domen |
| `VPS_USER` | SSH foydalanuvchi (`root` yoki `docker` guruhidagi user) |
| `VPS_PASSWORD` | shu foydalanuvchining SSH paroli |
| `VPS_PORT` | ixtiyoriy, standart `22` |
| `DEPLOY_PATH` | ixtiyoriy, standart `/opt/nasiya` |
| `ENV_FILE` | ixtiyoriy: `.env` ning to'liq matni. Berilsa, har deploy'da serverdagi `.env` shu bilan yoziladi |

**VPS ni bir martalik tayyorlash** (`root` bo'lmasa, avval `sudo mkdir -p /opt/nasiya && sudo chown $USER /opt/nasiya`
va `sudo usermod -aG docker $USER`):

```bash
ss -ltn                         # band portlarni ko'ring
mkdir -p /opt/nasiya
nano /opt/nasiya/.env           # .env.example asosida; HTTP_PORT ga BO'SH port yozing
```

`.env` da kamida: `DB_PASSWORD`, `APP_JWT_SECRET`, `APP_ADMIN_PASSWORD`, `HTTP_PORT` (masalan `8090`)
va `APP_CORS_ORIGINS=http://VPS_IP:8090`. Tizim: `http://VPS_IP:8090`.

Serverda allaqachon nginx/caddy (80/443) ishlayotgan bo'lsa, `.env` ga `HTTP_BIND=127.0.0.1` qo'ying
va mavjud nginx'da domen uchun `proxy_pass http://127.0.0.1:8090;` qo'shing — shunda 8090 tashqariga
ochilmaydi. Qolganini (`docker-compose.prod.yml`, `deploy/*.sh`) workflow o'zi ko'chiradi;
serverda build qilinmaydi — tayyor image'lar `ghcr.io` dan tortiladi.

### Namoyish (demo) ma'lumotlari

`deploy/seed/demo_seed.sql` **barcha bo'limlar** uchun namoyish ma'lumotini yuklaydi va
**idempotent**: qayta ishga tushirilsa mavjud demo yozuvlar takrorlanmaydi.

Qamrov: 32 mijoz, 16 mahsulot, oxirgi 6 oyga taqsimlangan **40 ta shartnoma** (~100 mln so'm savdo),
72 ta to'lov, arizalar (kutilayotgan/tasdiqlangan/shartnomaga aylangan/rad etilgan/hamkorga topshirilgan),
kredit tarixi hisobotlari, OTP va biometrik verifikatsiya, undirish harakatlari, sud ishlari, hamkor
(Alif/Uzum/TBC) savdolari, yetkazib beruvchilar va xarid buyurtmalari, hujjatlar jurnali, SMS
bildirishnomalar jurnali, 2 ta demo filial, 5 ta demo xodim (tizimga kira olmaydi — faqat KPI va
filial hisobotlari uchun), KPI rejalari va 3 ta shaxsiy rol. Sanalar ishga tushirilgan kunga
nisbatan hisoblanadi.

Yuklash usullari:

1. **Deploy qadamida avtomatik** — GitHub repo *Settings → Variables* da `SEED_DEMO=1` qo'ying.
   Keyingi deploy'da `deploy/remote-deploy.sh` baza ko'tarilgach seed'ni o'zi ishga tushiradi
   (idempotent bo'lgani uchun har deploy'da xavfsiz).
2. **Serverda qo'lda** (tavsiya etiladi):
   ```bash
   cd /opt/nasiya
   ./deploy/seed-demo.sh            # yuklash
   ./deploy/seed-demo.sh --remove   # faqat demo ma'lumotlarni o'chirish
   ```
3. **Actions orqali** — *Actions → "Demo ma'lumotlar" → Run workflow* (`seed` yoki `remove`).
4. **To'g'ridan-to'g'ri psql bilan**:
   ```bash
   docker compose -f docker-compose.prod.yml exec -T db \
     sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
     < deploy/seed/demo_seed.sql
   ```

`remove` faqat demo yozuvlarni (to'lovlari, hujjatlari va jurnallari bilan) o'chiradi; haqiqiy
shartnoma ochilgan demo mijoz/mahsulot bo'lsa, ular saqlanadi.

> **Haqiqiy ishga tushishdan oldin `--remove` qiling.** Demo mijozlarning telefon raqamlari tasodifiy;
> `APP_SMS_PROVIDER=eskiz` yoqilsa, kunlik eslatmalar shu raqamlarga ketadi.

## 2. Dasturchi uchun: lokal ishga tushirish

Kerak bo'ladi: JDK 21, Node 22, Docker. (Gradle kerak emas — `./gradlew` o'zi yuklab oladi.)

```bash
# 1) PostgreSQL
docker run -d --name nasiya-pg -p 5432:5432 \
  -e POSTGRES_DB=nasiya -e POSTGRES_USER=nasiya -e POSTGRES_PASSWORD=nasiya postgres:16-alpine

# 2) Backend (dev profili: demo ma'lumotlar + admin/admin123)
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'
# Swagger: http://localhost:8080/api/docs

# 3) Frontend (boshqa terminalda)
cd frontend
npm install
npm run dev          # http://localhost:5173  (/api -> localhost:8080 ga proksi)
```

Testlar: `cd backend && ./gradlew test` (murobaha, scoring va to'lov taqsimoti).
Jar yig'ish: `./gradlew bootJar` → `backend/build/libs/installment-platform-0.1.0.jar`.

## 3. Arxitektura

### Backend modullari (`backend/src/main/java/uz/installment/`)

| Paket | Vazifasi |
|---|---|
| `security` | JWT login, rollar (ADMIN, CREDIT_OFFICER, COLLECTOR, CASHIER, BRANCH_MANAGER, RISK_ANALYST, LEGAL_OFFICER), shaxsiy rollar, bo'lim darajasidagi ruxsatlar, foydalanuvchilar va xodim komplayens ma'lumotlari |
| `branch` | Filiallar: xodim va shartnoma filialga bog'lanadi, filial kesimidagi hisobot va oylik reja |
| `client` | Mijozlar (JShShIR bo'yicha takrorlanmaydi), kredit tarixi (KATM) yozuvlari |
| `application` | Nasiya arizalari: PENDING → APPROVED → CONTRACTED / REJECTED / PARTNER; rad etilganlari ham saqlanadi |
| `verification` | Mijozni tasdiqlash: SMS OTP va biometrik natija (rejim sozlamada) |
| `partner` | Hamkor provayderlar (Alif / Uzum / TBC): limit kalkulyatori, tavsiya, hamkorga topshirish |
| `analytics` | Chuqur tahlil: kategoriyalar, muddatlar, aging, filial/menejer reytingi, foyda hisoboti |
| `warehouse` | Ombor tahlili, yetkazib beruvchilar va xarid buyurtmalari |
| `product` | Kategoriyalar va mahsulotlar (narx, standart ustama, muddat oralig'i, ombor) |
| `scoring` | 100 ballik scoring, A/B/C/D risk toifasi, stop-faktorlar |
| `contract` | Murobaha hisob-kitobi, ko'p mahsulotli savat, shartnoma va to'lov jadvali |
| `payment` | To'lov qabul qilish; to'lov eng eski oydan boshlab taqsimlanadi |
| `collection` | Muddati o'tgan qarzlar, Soft / Hard / Legal bosqichlari, va'dalar, mas'ul xodim, sud va MIB ishlari |
| `document` | Shartnoma, talabnoma va da'vo arizasi PDF'lari (tahrirlanadigan shablonlardan, OpenPDF) |
| `kpi` | Operator KPI: arizalar, shartnomalar, savdo, tushum, undirish; oklad + savdo bonusi + KPI bonusi |
| `forecast` | Pul oqimi prognozi va What-If simulyatori: stress-test, jalb qilingan mablag', kapital talabi, hamkor kanali |
| `notification` | SMS (Eskiz) va Telegram shablonlari, jurnal |
| `jobs` | Kunlik job: kechikishlarni yangilash va eslatmalar (09:00 Toshkent vaqti) |
| `dashboard` | KPI'lar va grafiklar |
| `settings` | Kompaniya/brend sozlamalari |

### Murobaha qoidasi

```
moliyalashtirilgan = narx − boshlang'ich to'lov
ustama             = moliyalashtirilgan × ustama%     (bir marta, shartnomada qat'iy)
savdo narxi        = narx + ustama
oylik to'lov       = (moliyalashtirilgan + ustama) / muddat   (yaxlitlash qadami sozlamada)
```

Kechikish uchun foiz yoki jarima qo'shilmaydi. Har bir oylik to'lov buxgalteriya uchun
asosiy qarz va ustama qismlariga ajratib saqlanadi. Talabnoma, da'vo arizasi va prognozda ham
faqat to'lanmagan savdo narxi hisoblanadi.

### Ruxsatlar va filiallar

- Har bir rolning standart ruxsatlari `Role.defaultPermissions()` da. Administrator
  **Sozlamalar → Rollar va ruxsatlar** da rol ruxsatlarini, **Foydalanuvchilar** da esa alohida
  xodimga shaxsiy ruxsatlarni bera oladi. Administrator har doim barcha ruxsatlarga ega.
- Ruxsatlar har so'rovda bazadan (keshlangan) olinadi — o'zgarish tokenni yangilamasdan amal qiladi,
  o'chirilgan xodim darhol tizimdan chiqadi.
- Filialga biriktirilgan xodim `ALL_BRANCHES` ruxsatisiz faqat o'z filiali shartnomalari, to'lovlari,
  undirish ro'yxati va KPI'sini ko'radi. Filialsiz xodim (bosh ofis) — barchasini.

### Arizalar va savat

Nasiya avval **ariza** bo'ladi (`applications`): skoring natijasiga ko'ra `APPROVED` (shartnomaga tayyor),
`PENDING` (C toifa — tasdiqlash huquqi bor xodim qaror qiladi) yoki `REJECTED` (sababi bilan saqlanadi).
Shartnoma bir nechta mahsulotni o'z ichiga olishi mumkin (`contract_items`: mahsulot × miqdor); muddat
barcha mahsulotlarning umumiy oralig'ida, standart ustama — qator summalari bo'yicha o'rtacha. Ariza
bo'yicha tasdiqlash/rad etish, shartnoma tuzish va hamkorga topshirish **Arizalar** sahifasida.

### Mijoz tasdiqlash (verifikatsiya)

**Sozlamalar → Tasdiqlash va moliya** da rejim tanlanadi: `NONE`, `OTP`, `BIOMETRIC`, `ANY`, `BOTH`.
SMS OTP mijoz telefoniga yuboriladi (5 ta urinish, muddat sozlanadi; kod faqat `dev` profilida javobda
ko'rinadi). Biometrik tekshiruv uchun tashqi tizim (MyID, Face ID) natijasi — moslik foizi va jonlilik —
xodim tomonidan kiritiladi: bu tizim biometrik provayder bilan **to'g'ridan-to'g'ri integratsiya qilinmagan**.
Tasdiqlash 30 daqiqa amal qiladi va ariza yuborilganda tekshiriladi.

### Hamkor provayderlar

Alif / Uzum / TBC shartlari (komissiya, maksimal muddat, minimal boshlang'ich to'lov, DTI) **Sozlamalar →
Hamkorlar** da. Limit = daromad × DTI × xavf koeffitsiyenti (A 1.0, B 0.8, C 0.5, D 0) × muddat — bu
taxmin; yakuniy qaror hamkor tizimida. Ichki limit yetsa ichki murobaha, aks holda eng arzon komissiyali
mos hamkor tavsiya etiladi. Topshirilgan savdoda shartnoma tuzilmaydi, tovar ombordan chiqadi, komissiya yoziladi.

### Operator KPI

Ball = rejasi qo'yilgan ko'rsatkichlar bajarilishining vaznli o'rtachasi (har biri 150% bilan cheklanadi):
arizalar soni, shartnomalar soni, savdo hajmi, kassa tushumi, muddati o'tgandan undirilgan summa, undirish harakatlari,
portfel sifati (`100 − 2 × muddati o'tgan ulush`). Bonus = bonus fondi × ball (60% dan past bo'lsa — 0,
yuqorisi 120%). Jami to'lov = oklad + savdo bonusi (savdo summasi × %) + KPI bonusi. Vaznlar va chegaralar
**Xodimlar KPI → KPI formulasi** da, oklad va bonus foizi xodim kartasida sozlanadi; xodim kartasida har
bir ko'rsatkich yakuniy ballga qancha ball qo'shgani ko'rsatiladi.

### Prognoz

Jadvaldagi kelgusi to'lovlar × o'z vaqtida yig'ilish ulushi (oxirgi 6 oy), muddati o'tgan qarzdan oylik
undirish (oxirgi 3 oy) va yangi nasiyalar (mavsumdan tozalangan o'rtacha savdo × oy koeffitsiyenti).
Koeffitsiyentlar standart (O'zbekiston chakana savdosi) yoki 12 oylik tarixdan hisoblanadi.

**Simulyator** qo'shimcha parametrlari: ustama, boshlang'ich to'lov, muddat, jalb qilingan mablag' narxi,
makro-stress (past / o'rta / yuqori) va hamkorlar orqali o'tadigan savdo ulushi. Natija: kapital talabi
(eng chuqur kassa kamomadi), o'z va jalb qilingan mablag' ulushi, jalb narxi, sof foyda (foyda + hamkor
marjasi − jalb narxi − OPEX), tavsiya etilgan zaxira va stress ta'siri. OPEX, o'z kapitali va jalb narxi
**Sozlamalar → Tasdiqlash va moliya** da.

### Komplayens va rollar

- Xodimga JShShIR, pasport va nasiya cheklovi (`allow_nasiya`, standart — yo'q) kiritiladi. Xodim JShShIR'iga
  ariza berilsa, `EMPLOYEE_RESTRICTED` stop-faktori ishlaydi.
- **Shaxsiy rol**: nom, rang va ruxsatlar to'plami; xodimga biriktiriladi, to'plam o'zgarsa darhol amal qiladi.
- Kredit tarixi (KATM) hisobotlari qo'lda kiritiladi — byuro bilan avtomatik integratsiya yo'q.
- Sud paketi (ZIP): shartnoma, talabnoma va da'vo arizasi PDF'lari (Undirish → Legal).

### SaaS'ga tayyorgarlik

- Barcha biznes jadvallarida `tenant_id` bor (hozir doim `1`, `TenantContext`).
- Brend, rang, logo, shartnoma prefiksi va limitlar kodda emas, `tenant_settings` jadvalida.
- SMS va Telegram `MessageSender` interfeysi orqali ulanadi, provayderni almashtirish oson.
- Paket nomi neytral: `uz.installment`.

SaaS'ga o'tishda qilinadiganlar: `TenantContext` ni JWT'dan to'ldirish, Hibernate filtri yoki
PostgreSQL RLS yoqish, tenant ro'yxatdan o'tkazish, `contract_no_seq` ni tenant bo'yicha
ajratish va bir nechta instansiya uchun ShedLock qo'shish.

## 4. Holat va keyingi qadamlar

**Tayyor:** mijozlar, mahsulotlar, scoring va stop-faktorlar, murobaha kalkulyatori,
shartnoma yaratish wizard'i, to'lov jadvali, kassada to'lov qabul qilish, undirish
(Soft / Hard / Legal, va'dalar, sud ishlari), SMS/Telegram shablonlari va kunlik eslatmalar,
shartnoma / talabnoma / da'vo arizasi PDF'lari, filiallar, bo'lim darajasidagi ruxsatlar,
operator KPI (oklad va jami to'lov bilan), pul oqimi prognozi va What-If simulyatori, dashboard, sozlamalar,
arizalar jarayoni, ko'p mahsulotli savat, mijoz verifikatsiyasi, hamkor provayderlar, chuqur analitika,
ombor tahlili va xarid buyurtmalari, shaxsiy rollar, xodim komplayensi, kredit tarixi, sud paketi (ZIP),
Docker bilan deploy va zaxira nusxa.

**Keyingi bosqich:**
1. Payme va Click merchant API (webhook, `external_id` bo'yicha idempotentlik tayyor).
2. KATM va MIB bilan avtomatik integratsiya (hozir kredit tarixi qo'lda kiritiladi); MyID/biometrik provayder va Alif/Uzum/TBC API'lari bilan ulanish.
3. E-IMZO bilan shartnomani elektron imzolash (PDF tayyor).
4. Telegram bot: mijoz `/start` bosib, `telegram_chat_id` ni ulaydi.
5. Hisobotlar: Excel eksport.
6. Audit log: kim, qachon, nimani o'zgartirdi.

> **Eslatma:** backend kodi birinchi marta `./gradlew test` yoki `docker compose build` da
> kompilyatsiya qilinadi. Birinchi yig'ishda xato chiqsa, xabarini yuboring.

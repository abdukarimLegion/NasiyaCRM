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

Demo ma'lumotlar bilan sinab ko'rish uchun `.env` da `SPRING_PROFILES_ACTIVE=dev` qo'ying.
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
| `security` | JWT login, rollar (ADMIN, CREDIT_OFFICER, COLLECTOR, CASHIER), foydalanuvchilar |
| `client` | Mijozlar (JShShIR bo'yicha takrorlanmaydi) |
| `product` | Kategoriyalar va mahsulotlar (narx, standart ustama, muddat oralig'i, ombor) |
| `scoring` | 100 ballik scoring, A/B/C/D risk toifasi, stop-faktorlar |
| `contract` | Murobaha hisob-kitobi, shartnoma va to'lov jadvali |
| `payment` | To'lov qabul qilish; to'lov eng eski oydan boshlab taqsimlanadi |
| `collection` | Muddati o'tgan qarzlar, bosqichlar va undiruvchi harakatlari |
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
asosiy qarz va ustama qismlariga ajratib saqlanadi.

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
shartnoma yaratish wizard'i, to'lov jadvali, kassada to'lov qabul qilish, undirish ro'yxati,
SMS/Telegram shablonlari va kunlik eslatmalar, dashboard, sozlamalar, foydalanuvchilar,
Docker bilan deploy va zaxira nusxa.

**Keyingi bosqich:**
1. Payme va Click merchant API (webhook, `external_id` bo'yicha idempotentlik tayyor).
2. KATM (kredit tarixi) va MIB integratsiyasi: `ScoringService` dagi FRAUD/COURT/OVERDUE avtomatlashadi.
3. Shartnoma PDF (JasperReports yoki OpenPDF) va E-IMZO.
4. Telegram bot: mijoz `/start` bosib, `telegram_chat_id` ni ulaydi.
5. Hisobotlar: Excel eksport, menejer KPI.
6. Audit log: kim, qachon, nimani o'zgartirdi.

> **Eslatma:** backend kodi birinchi marta `./gradlew test` yoki `docker compose build` da
> kompilyatsiya qilinadi. Birinchi yig'ishda xato chiqsa, xabarini yuboring.

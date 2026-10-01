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

## 1.1. CI/CD (GitHub Actions)

| Workflow | Qachon | Nima qiladi |
|---|---|---|
| `.github/workflows/ci.yml` | har push va PR (`main`) | backend `./gradlew build` (testlar bilan), frontend `lint` + `tsc` + `vite build` |
| `.github/workflows/deploy.yml` | qo'lda (*Run workflow*) yoki `v*` tegi | backend/frontend image'larini yig'ib `ghcr.io` ga yuklaydi, serverga SSH orqali chiqib yangilaydi va sog'lomligini tekshiradi |

Deploy mijoz serveriga chiqadi, shuning uchun **har push'da avtomatik ishlamaydi**.
Relizni chiqarish: `git tag v1.0.0 && git push origin v1.0.0`.

**Repo secret'lari** (Settings → Secrets and variables → Actions):

| Secret | Izoh |
|---|---|
| `SSH_HOST` | server IP yoki domen |
| `SSH_USER` | deploy foydalanuvchisi (docker guruhida bo'lsin) |
| `SSH_KEY` | shu foydalanuvchining **private** SSH kaliti (to'liq matn) |
| `SSH_PORT` | ixtiyoriy, standart `22` |
| `DEPLOY_PATH` | ixtiyoriy, standart `/opt/nasiya` |

**Serverni bir martalik tayyorlash:** Docker o'rnatilgan bo'lsin, `DEPLOY_PATH` papkasini
yaratib, ichiga to'ldirilgan `.env` faylini qo'ying (`.env.example` dan). Qolganini
(`docker-compose.prod.yml`, `deploy/*.sh`) workflow o'zi ko'chiradi. Serverda build
qilinmaydi — tayyor image'lar `ghcr.io` dan tortiladi.

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

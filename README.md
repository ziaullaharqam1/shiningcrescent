# Shining Crescent

Fruit and dry-fruit **trading house**: retail + wholesale cart, lot inventory (FEFO), procurement, QC, fulfillment, invoices, RBAC, notifications, and audit.

## Stack

- **Frontend:** React 18, Vite, Tailwind CSS
- **Backend:** Spring Boot 3.4, Java 21, Spring Security JWT, JPA
- **Database:** PostgreSQL 16
- **Events:** In-process event bus by default; Apache Kafka when `KAFKA_ENABLED=true`

## Run

PostgreSQL on `localhost:5432` is required. Either:

```bash
docker compose up -d postgres
```

or use a local server (create database `shiningcrescent`, user `rc` / `rc_secret`).

Then:

```bash
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

Open [http://localhost:5173](http://localhost:5173). API is proxied to port 8080.

Optional Kafka:

```bash
docker compose --profile kafka up -d
KAFKA_ENABLED=true ./mvnw spring-boot:run
```

## Shopper login

The store login page supports:

- **Mobile OTP** — 6-digit SMS via [Twilio](https://www.twilio.com) (trial credit is free). A new retail buyer account is created on first verify so the shopper can cart and check out.
- **Google** — OAuth redirect to `http://localhost:8080/login/oauth2/code/google`. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` (Web application client). After Google returns, the API issues a JWT and sends the browser back to `http://localhost:5173/login`.
- **Username / password** — staff and demo roles below.

Without Twilio env vars, the API logs the code and the login screen shows it (same idea as the Stripe test charge). Twilio trial can only SMS numbers you verify in the console.

```bash
export TWILIO_ACCOUNT_SID=ACxxxxxxxx
export TWILIO_AUTH_TOKEN=xxxxxxxx
export TWILIO_FROM_NUMBER=+1xxxxxxxxxx
export GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com
export GOOGLE_CLIENT_SECRET=xxxx
```

## Demo logins (password in parentheses)

| User | Role |
| --- | --- |
| `buyer` (`Buyer@123`) | Retail customer |
| `wholesale` (`Trade@123`) | B2B buyer (trade prices) |
| `admin` (`Admin@123`) | Super admin |
| `manager` (`Mgr@123`) | Trading manager |
| `procure` (`Proc@123`) | Procurement |
| `warehouse` (`Ware@123`) | Warehouse |
| `qa` (`Qa@123`) | Quality inspector |
| `sales` (`Sales@123`) | Wholesale sales |
| `merch` (`Merch@123`) | Catalog merchandiser |
| `finance` (`Fin@123`) | Finance |
| `supplier` (`Supp@123`) | Grower / packer portal |

## Roles and screens

Permissions are `SCREEN:ACTION` (VIEW, CREATE, UPDATE, APPROVE, DELETE) and are editable under **Roles**.

- **CUSTOMER** — marketplace, cart, own orders  
- **SUPPLIER** — assigned purchase orders (acknowledge / print), own lots and QC results. No company-wide sales reports, no GRN, no approve. 
- **CATALOG_MERCHANDISER** — product/category masters, submit for publish  
- **TRADING_MANAGER** — publish SKUs, approve POs and large orders  
- **PROCUREMENT_OFFICER** — suppliers, POs  
- **WAREHOUSE_KEEPER** — GRN, lots, pick-pack-ship  
- **QUALITY_INSPECTOR** — moisture/grade, hold/release lots  
- **SALES_EXECUTIVE** — sales order workflow  
- **FINANCE_OFFICER** — credit hold release, invoices, payments  
- **SUPER_ADMIN** — everything  

## Workflows

1. **Catalog:** Draft → submit → manager publish  
2. **Procure:** Draft PO → submit → approve → send to supplier → GRN (quarantine lot) → QC pass → available stock. Creating a product also raises an opening PO of 3 kg with any active supplier, auto-approves QA, and posts an **available** lot.
3. **Order-to-cash:** Cart → place → (credit hold if over limit) → confirm → pick (FEFO) → pack → ship → deliver → invoice → payment  
4. **Returns:** Delivered → return requested → returned  
5. **Agent:** In-app chat (typed or voice) plus WhatsApp Cloud API / WAPI webhook at `/api/agent/wapi/webhook` can create/edit products and POs, approve or quarantine, generate daily/monthly reports, and add to cart.

Daily and monthly reports export as Excel (summary sheet, then sales / invoices / POs / quality tables).

WhatsApp (WAPI) optional env: `WAPI_ENABLED`, `WAPI_TOKEN`, `WAPI_PHONE_NUMBER_ID`, `WAPI_VERIFY_TOKEN`. Optional `OPENAI_API_KEY` improves natural-language planning.

# Credit / Debit Notes API Map (read-only backend audit)

Audit scope: backend source present in this repository as of 2026-11-19. No application code was changed for this document. Secret values are intentionally omitted.

## 1. Base URL and authentication

`server.js` loads environment configuration and listens on `process.env.PORT || 3000` (HTTP + Socket.IO). No public host, `BASE_URL`, API context path, or version prefix is defined in `server.js`, `src/app.js`, or the inspected config. `FRONTEND_BASE_URL` is a CORS allowlist, not this backend's base URL. Therefore an absolute URL cannot be stated from code without guessing. In this document, `{BASE_URL}` means the deployment's externally configured origin, which is not present in the repository; mounted route paths after it are exact.

The API prefix is `/api`; there is no version prefix. Note routes mount at `/api/credit-debit-notes`; order routes mount at `/api/orders`. Routes normally use `authMiddleware`, which accepts `Authorization: Bearer <JWT>` or the `token` cookie. It loads user role and builds seller context from the verified token's `seller_id`. Existing note controller seller resolution can fall back to user IDs (`req.user._id`/`req.seller._id`) when seller ID is absent. Note routes currently do **not** add `requireRole` or `requireModule`; no re-auth/OTP is required for note cancellation. The general `requireModule("billing")` middleware checks the Staff token's module; Seller and Admin pass. No note endpoint currently invokes it.

Mounted payment webhook code: `POST /api/orders/webhook/razorpay` is registered but its handler returns 501. `src/routes/payment.routes.js` has a `/webhook` route but `src/app.js` does not mount that router, so it has no reachable URL in this app. No `.env.example` was found. `.env` was not read for values or copied into this report.

## 2. Capability inventory

| Capability | Status | Method | FULL URL | Source file |
|---|---|---|---|---|
| a. Invoice note eligibility | PARTIAL | Existing validation during POST only | `{BASE_URL}/api/credit-debit-notes/{credit\|debit}` (no eligibility endpoint) | `src/controller/creditDebitNote.controller.js:69-134` |
| b. Create CN/DN on invoice | PARTIAL | POST | `{BASE_URL}/api/credit-debit-notes/{credit\|debit}` | `src/routes/creditDebitNote.routes.js:14`; controller `:69-226` |
| c. Note detail with settlement timeline | PARTIAL | GET | `{BASE_URL}/api/credit-debit-notes/{id}` | `src/routes/creditDebitNote.routes.js:13`; controller `:34-67` |
| d. Note list with requested filters | PARTIAL | GET | `{BASE_URL}/api/credit-debit-notes` | `src/routes/creditDebitNote.routes.js:12`; controller `:12-32` |
| e. Cancel note | PARTIAL | PATCH | `{BASE_URL}/api/credit-debit-notes/{id}/cancel` | `src/routes/creditDebitNote.routes.js:15`; controller `:229-255` |
| f. Full invoice cancellation / full remaining-value CN | MISSING | — | — | No implementation found |
| g. Note refund through original seller-owned Razorpay capture | MISSING | — | — | No note refund implementation. Booking-only refund helper is not invoice/note scoped (`src/controller/booking.controller.js:212-264`) |
| h. Manual cash/bank/UPI refund record | MISSING | — | — | No refund ledger/route found |
| i. Apply CN balance to another invoice | MISSING | — | — | No credit application implementation found |
| j. Note refund status | MISSING | — | — | No note refund resource/status endpoint found |
| k. Note PDF generation/download | MISSING | — | — | No note PDF route/controller found |
| l. Customer-wise available CN balance | MISSING | — | — | No customer credit balance endpoint/ledger found |
| m. Invoice detail with payment IDs and paid/due amounts | PARTIAL | GET | `{BASE_URL}/api/orders/{id}` | `src/routes/order.routes.js:111`; controller `src/controller/order.controller.js:3300-3370` |
| n. Razorpay webhook with signature verification and event dedupe | PARTIAL | POST | `{BASE_URL}/api/orders/webhook/razorpay` | `src/routes/order.routes.js:104-108`; handler `src/controller/order.controller.js:4274-4275` |
| o. Audit log, risk permissions, re-auth/OTP | PARTIAL | Existing auth/role/OTP routes only | No dedicated note audit/re-auth URL | `src/middlewares/auth.middleware.js`; `src/middlewares/requireModule.middleware.js`; note routes above |
| Discount adjustment on a note | MISSING | — | — | No note discount adjustment fields/logic found |

The repository does not contain the seller-facing dashboard/app, so client-side call patterns cannot be inferred from this backend scan.

## 3. Existing PRESENT/PARTIAL API contracts and gaps

All URLs below are mounted paths, not complete absolute URLs: the backend deployment origin is not configured in this source tree. All note endpoints use the same auth mechanism described in Section 1. None of the note endpoints currently applies a role/module guard. Controller seller scoping is `seller_id` in database queries, but `getSellerId()` has non-token fallback values; this is a cross-tenant risk if token context is malformed/missing.

### 3.1 Note list — PARTIAL

- **Method/path:** `GET {BASE_URL}/api/credit-debit-notes`
- **Auth/scope:** Bearer JWT or token cookie; `CreditDebitNote.find({seller_id: getSellerId(req)})`; population exposes order customer name, phone, invoice number.
- **Query:** none read by the controller.
- **Success (exact envelope):**

```json
{"success":true,"notes":[{"_id":"NOTE_ID","seller_id":"SELLER_ID","order_id":{"_id":"ORDER_ID","customer_name":"Asha Rao","customer_phone":"9876543210","invoice_number":"INV-0007"},"note_type":"credit","note_number":"CN-0001","against_invoice_number":"INV-0007","invoice_number":"INV-0007","previous_note_number":null,"previous_note_id":null,"reason":"Item returned","description":"","items":[],"subtotal":100,"tax_amount":5,"total_amount":105,"correct_amount":105,"status":"issued","cancelled_at":null,"createdAt":"2026-11-19T10:00:00.000Z","updatedAt":"2026-11-19T10:00:00.000Z"}]}
```

- **Errors:** `500 {"success":false,"message":"Unable to load notes"}`.
- **Source:** `src/routes/creditDebitNote.routes.js:12`; `src/controller/creditDebitNote.controller.js:12-32`.
- **Gaps:** no pagination, type/status/date/customer/invoice/reason filters, counts/summary, customer credit balance, canonical `Active` status, settlement block/timeline, or explicit payment/refund info. Actual active status is `issued`.

### 3.2 Note detail — PARTIAL

- **Method/path:** `GET {BASE_URL}/api/credit-debit-notes/{id}`
- **Auth/scope:** Bearer JWT or token cookie; note lookup includes `_id` and seller ID; order population includes customer name/phone/invoice.
- **Query/body:** none.
- **Success:** `{"success":true,"note":<CreditDebitNote document>}`. Document fields match the list sample in 3.1.
- **Errors:** 400 `{"success":false,"message":"Invalid note id"}`; 404 `{"success":false,"message":"Note not found"}`; 500 `{"success":false,"message":"Unable to load note"}`.
- **Source:** `src/routes/creditDebitNote.routes.js:13`; `src/controller/creditDebitNote.controller.js:34-67`.
- **Gaps:** no settlement/refund timeline, payment IDs, customer invoice payments, tax treatment/discount adjustment metadata, standard Draft/Issued/Partially Settled/Settled status model.

### 3.3 Create CN or DN — PARTIAL

- **Method/path:** `POST {BASE_URL}/api/credit-debit-notes/{type}` where `type` is `credit` or `debit`.
- **Auth/scope:** Bearer JWT or token cookie. Order lookup scopes `{_id: order_id, seller_id: sellerId}`. Seller ID is currently derived through fallback helper, rather than strictly token-only.
- **Body (exact fields read):**

```json
{"order_id":"ORDER_OBJECT_ID","reason":"Item returned","remarks":"Optional note","items":[{"item_id":"ORDER_ITEM_OBJECT_ID","quantity":1,"rate":100,"tax_percent":5}]}
```

`order_id`: ObjectId string; `reason`: required non-empty string; `remarks`: optional string; `items`: non-empty array. Each item requires an existing order item ID and positive numeric quantity; `rate` and `tax_percent` are optional nonnegative numeric overrides. Rate/tax default from order item price/tax_percent. Tax is calculated as amount × tax percent / 100 and rounded to 2 decimal places. Note number is seller-specific `CN-0001` or `DN-0001` (no fiscal year component).
- **Success:** HTTP 201, `{"success":true,"note":<created note>}`. Note stores `seller_id`, `order_id`, `note_type`, `note_number`, `against_invoice_number`, `invoice_number`, `previous_note_number`, `previous_note_id`, `reason`, `description`, `items`, `subtotal`, `tax_amount`, `total_amount`, `correct_amount`, `status`, timestamps.
- **Errors:** 400 `Invalid note type`, `Invalid order`, `Reason is required`, `Select at least one item`, `Invoice number not generated yet. Print the invoice first.`, `Duplicate item in note`, `Item not part of this invoice`, `Invalid quantity for <name>`, or `<Item>: only <n> quantity left to credit`; 404 `Invoice not found`; generic 500 `Unable to create note`.
- **Source:** `src/routes/creditDebitNote.routes.js:14`; `src/controller/creditDebitNote.controller.js:69-226`; numbering `src/utils/noteNumber.js:1-40`; schema `src/models/creditDebitNote.model.js:17-69`.
- **Gaps:** no `client_request_id`/idempotency; monetary float/2-decimal values instead of paise integers; no amount-only mode; credit is capped per-item quantity but not cumulative remaining invoice payable/value; debit can exceed invoice; cancelled invoice/order not rejected; no full invoice cancellation; no discount adjustment/tax treatment enum; no note status Draft/Issued/Partially Settled/Settled/Cancelled; status is `issued`; no immutable audit event or settlement ledger. Prior note is selected for same order/type among non-cancelled notes only.

### 3.4 Cancel note — PARTIAL

- **Method/path:** `PATCH {BASE_URL}/api/credit-debit-notes/{id}/cancel`.
- **Auth/scope:** Bearer JWT or token cookie; `findOneAndUpdate` includes `_id`, seller ID and `status != cancelled`.
- **Body/query:** none.
- **Success:** `{"success":true,"note":<updated note>}`; sets `status:"cancelled"`, `cancelled_at`.
- **Errors:** 404 `{"success":false,"message":"Note not found or already cancelled"}`; 500 `{"success":false,"message":"Unable to cancel note"}`. Invalid ObjectId does not get a dedicated 400 check here.
- **Source:** `src/routes/creditDebitNote.routes.js:15`; `src/controller/creditDebitNote.controller.js:229-255`.
- **Gaps:** uses PATCH rather than requested POST; no reason/body, no re-auth/OTP, no permission/module guard, no audit trail, no settled-refund reversal/settlement constraints, status vocabulary mismatch.

### 3.5 Order/invoice detail — PARTIAL

- **Method/path:** `GET {BASE_URL}/api/orders/{id}`.
- **Auth/scope:** Bearer JWT or token cookie. Seller request uses `_id` and seller context. Customer request is restricted using customer phone and optional seller ID. Controller also contains query/header fallbacks for legacy context. Route uses `authMiddleware`.
- **Query/body:** no required params beyond `id`.
- **Success:** `{"success":true,"data":<Order document with populated items.product_id, seller_id and delivery_boy_id>,"order":<same Order document>}`. Order fields include `invoice_number`, `invoice_seq`, KOT fields, customer name/phone, item snapshots, `subtotal`, discounts, `tax_amount`, `total_amount`, `payment_method`, `payment_status`, `razorpay_order_id`, table/order type and return fields. Seller population selects store/contact/tax settings.
- **Errors:** 400 `Invalid Order ID format!`; 401 `Unauthorized! No seller or customer context found.`; 404 `Order not found.`; 500 `Server error fetching order details.`
- **Source:** `src/routes/order.routes.js:111`; `src/controller/order.controller.js:3300-3370`; schema `src/models/order.model.js:1-287`.
- **Gaps:** no per-invoice payment ledger, Razorpay payment ID is not stored on Order schema, no cumulative `captured/refunded/paid/due` breakdown, no seller-specific payment gateway credential entity shown. Values are rupee Numbers, not integer paise. Not a note eligibility endpoint.

### 3.6 Razorpay webhook route — PARTIAL (nonfunctional handler)

- **Method/path:** `POST {BASE_URL}/api/orders/webhook/razorpay`.
- **Auth/scope:** no JWT (provider webhook route). `src/app.js` applies raw JSON parser for this exact path and the route also declares `express.raw`.
- **Body/query:** intended raw `application/json`; handler ignores it.
- **Success/current response:** HTTP 501 `{"success":false,"message":"Not implemented"}`.
- **Source:** `src/routes/order.routes.js:104-108`; `src/app.js:60-68`; handler `src/controller/order.controller.js:4274-4275`.
- **Gaps:** no signature validation, event-type handling, dedupe/event storage, payment/refund updates, seller/account mapping, or idempotency. `src/routes/payment.routes.js` defines another `/webhook` but is not mounted by `src/app.js`, so it is not an additional reachable URL.

### 3.7 Existing related print and refund behavior

Order print routes exist: `POST {BASE_URL}/api/orders/{id}/print-numbers` accepts optional `{ "mode": "kot|invoice|both" }` and returns `{success:true,numbers:{kot_number,kot_printed_at,invoice_seq,invoice_number,invoice_printed_at}}`; `POST .../{id}/print-confirm` has same mode and returns print timestamps. Both use auth and seller-scoped order lookup. Source: `src/routes/order.routes.js:49-50`, `src/controller/prints.controller.js:1-151`. These are invoice/KOT markers, not note PDF/print APIs.

Booking cancellation/edit uses Razorpay refund calls with `razorpay_payment_id`, tracks refund amount in Booking payment records, and has seller scoping in booking controller paths; it is not a note refund API and cannot satisfy invoice refund status/timeline. Source: `src/controller/booking.controller.js:193-264,1196-1264`, `src/controller/bookingEdit.controller.js:250-270,375-420`. No seller-specific Razorpay credential-per-seller model was found; `src/config/razorpay.js` configures one process-level client from environment keys (names only; values omitted).

## 4. Missing or partial capabilities: proposed contracts and static mocks

These are **suggestions only**, not existing URLs or implemented behavior. Suggested paths follow the requested invoice/note structure. Suggested status vocabulary: `Draft | Issued | Partially Settled | Settled | Cancelled`. Amounts in the mocks are integer paise. Any actual refund must enforce seller ownership of the original captured payment and use that seller's configured Razorpay account; current source does not show such account configuration. The static mock data is illustrative only.

### Shared suggested note/settlement shape

```json
{"note_id":"NOTE_ID","note_number":"CN-FY-0001","type":"credit","status":"Partially Settled","invoice":{"id":"INVOICE_ID","number":"INV-2026-0007"},"previous_note":{"id":null,"note_number":null},"amount_paise":10500,"tax_treatment":"GST_REVERSAL","discount_adjustments":[],"settlement":{"refunded_paise":3000,"pending_refund_paise":2000,"manual_refund_paise":1000,"applied_paise":500,"remaining_paise":4000},"timeline":[{"event":"issued","at":"2026-11-19T10:00:00.000Z","actor_id":"SELLER_USER_ID"}]}
```

| Suggested API | Expected request | Expected success response | Expected error response | Mock |
|---|---|---|---|---|
| `GET /api/invoices/{id}/notes/eligibility` | Query none | `{success, invoice, eligibility:{can_credit,can_debit,can_full_cancel,remaining_credit_paise,remaining_qty,reason_codes}}` | 404 `INVOICE_NOT_FOUND`; 403 `INVOICE_NOT_OWNED` | [eligibility.json](credit-debit-mocks/eligibility.json) |
| `POST /api/invoices/{id}/notes` | `{client_request_id,type,reason,tax_treatment,items:[{invoice_item_id,quantity,amount_paise,discount_adjustment_paise}]}` | `{success,note:<shared shape>}` | 409 `IDEMPOTENCY_CONFLICT`; 422 `CREDIT_EXCEEDS_REMAINING` | [create-note.json](credit-debit-mocks/create-note.json) |
| `GET /api/notes/{id}` | None | `{success,note:<shared shape>,invoice,payments}` | 404 `NOTE_NOT_FOUND` | [note-detail.json](credit-debit-mocks/note-detail.json) |
| `GET /api/notes?type=&status=&from=&to=&customer_id=&invoice_number=&reason=&page=&limit=` | Query filters shown | `{success,notes:[...],pagination:{page,limit,total,pages}}` | 400 `INVALID_FILTER` | [note-list.json](credit-debit-mocks/note-list.json) |
| `POST /api/notes/{id}/cancel` | `{reason,client_request_id}` | `{success,note:<shared shape with status Cancelled>,timeline}` | 409 `NOTE_ALREADY_SETTLED` | [cancel-note.json](credit-debit-mocks/cancel-note.json) |
| `POST /api/invoices/{id}/full-credit-note` | `{client_request_id,reason,tax_treatment}` | `{success,note:<shared shape>,full_cancellation:true}` | 422 `NO_REMAINING_VALUE` | [full-cancellation.json](credit-debit-mocks/full-cancellation.json) |
| `POST /api/notes/{id}/refunds/razorpay` | Header `X-Refund-Idempotency`; body `{amount_paise,razorpay_payment_id,reason}` | `{success,refund:{id,status,amount_paise,provider_refund_id},note_settlement}` | 422 `REFUND_EXCEEDS_CAPTURED_BALANCE`; 502 `PROVIDER_REFUND_FAILED` | [razorpay-refund.json](credit-debit-mocks/razorpay-refund.json) |
| `POST /api/notes/{id}/refunds/manual` | `{client_request_id,amount_paise,method,reference,reason}` where method `cash|bank|upi` | `{success,refund:{id,status,amount_paise,method,reference},note_settlement}` | 422 `REFUND_EXCEEDS_REMAINING` | [manual-refund.json](credit-debit-mocks/manual-refund.json) |
| `POST /api/notes/{id}/credit-applications` | `{client_request_id,target_invoice_id,amount_paise}` | `{success,application:{id,amount_paise,source_note_id,target_invoice_id},note_settlement}` | 422 `CUSTOMER_MISMATCH`; 422 `CREDIT_EXCEEDS_REMAINING` | [credit-application.json](credit-debit-mocks/credit-application.json) |
| `GET /api/refunds/{id}` | None | `{success,refund:{id,note_id,status,method,amount_paise,provider_refund_id,created_at,updated_at}}` | 404 `REFUND_NOT_FOUND` | [refund-status.json](credit-debit-mocks/refund-status.json) |
| `GET /api/notes/{id}/pdf` | None | `application/pdf` stream; or JSON metadata if using async generation | 404 `NOTE_NOT_FOUND`; 409 `PDF_NOT_READY` | [note-pdf.json](credit-debit-mocks/note-pdf.json) |
| `GET /api/customers/{id}/credit-balance` | Optional `currency` | `{success,customer_id,available_credit_paise,credits:[{note_id,note_number,remaining_paise}]}` | 404 `CUSTOMER_NOT_FOUND` | [customer-credit-balance.json](credit-debit-mocks/customer-credit-balance.json) |
| `GET /api/invoices/{id}` | Query `include=payments` | `{success,invoice:{...},payments:[{provider,payment_id,captured_paise,refunded_paise}],paid_paise,due_paise}` | 404 `INVOICE_NOT_FOUND` | [invoice-payments.json](credit-debit-mocks/invoice-payments.json) |
| `POST /api/webhooks/razorpay` | Raw provider event + `X-Razorpay-Signature` | `{success:true,received:true,duplicate:false}` | 400 `INVALID_SIGNATURE`; 409 `EVENT_CONFLICT` | [razorpay-webhook.json](credit-debit-mocks/razorpay-webhook.json) |
| Audit/re-auth support | Proposed `POST /api/notes/{id}/reauth` `{otp}` and `GET /api/notes/{id}/audit` | Re-auth `{success,reauth_token,expires_at}`; audit `{success,events:[...]}` | 401 `REAUTH_REQUIRED`; 403 `MISSING_BILLING_PERMISSION` | [audit-reauth.json](credit-debit-mocks/audit-reauth.json) |

`POST /api/invoices/{id}/notes` is the proposed replacement contract for the existing `POST /api/credit-debit-notes/{credit|debit}`; it is not currently routed. The suggested Razorpay endpoint is likewise new. All proposed seller operations should use verified token `seller_id`, role Seller/Staff, Staff `billing` module, and server-side tenant checks. Webhook authentication must be signature-based and dedupe on provider event ID, not seller JWT.

## 5. Existing data model vs required

| Entity | Present fields/model | Missing for requested billing-note module |
|---|---|---|
| Invoice | No standalone Invoice model. Invoice is represented by Order fields: `invoice_number`, `invoice_seq`, `invoice_printed_at`; identity is Order `_id`. | Invoice entity/lifecycle, immutable totals snapshot/version, invoice void state, fiscal-year sequence format, invoice-level payment allocation. |
| Invoice items | Embedded `Order.items[]`: `product_id`, `name`, `quantity`, `price`, `variant`, `addons`, `instruction`. | Stable invoice-item identifier is embedded Mongo `_id` but not formally named; original/remaining credited quantity/value ledger; line-level tax and discount snapshot. |
| Order totals/customer/payment | Order has `seller_id`, customer name/phone, subtotal, discount fields, tax, total, payment method/status, `razorpay_order_id`, returns/refund amounts. | `razorpay_payment_id` field on Order, payment ledger, captured/refunded paise, amount paid/due, per-invoice payment allocations. Existing values are JS/Mongo Numbers typically representing rupees. |
| Credit/debit notes | `CreditDebitNote`: seller/order refs, type `credit|debit`, note number, invoice references, prior note refs, reason/description, item qty/rate/tax/amounts, totals, status `issued|cancelled`, cancelled_at, timestamps. Unique seller+note number index; order/type/date index. | Idempotency key, `CN-FY-0001` fiscal number, amount paise, invoice item reference contract, discount adjustments, tax treatment, Draft/Partially Settled/Settled states, settlement ledger, customer reference snapshot, full-cancel marker, audit events. |
| Note counter | `CreditDebitNoteCounter` schema exists for `credit_debit_note_counters`, key format comment `<NOTE_TYPE>:<SELLER_ID>:<ORDER_ID>`. | The active allocator imports `PrintCounter` (`print_counters`) instead; no fiscal-year counter metadata or idempotency record. |
| Refund records | No dedicated note refund model found. Booking model has embedded `payments[]` with Razorpay payment ID, amount/refunded and top-level payment/refund fields; Order has return request `refund_amount` and `returned_amount`. | Seller-owned gateway refund record, provider refund ID/status, original captured payment relation, method/manual reference, idempotency key, retry state, webhook event dedupe, note settlement timeline. |
| Seller payment gateway | `src/config/razorpay.js` creates a singleton from environment `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`. | Seller-scoped Razorpay credentials/account token and access control. No `seller_payment_gateway` model found. |
| Customer credit | Customer/User identity and order associations exist. | Credit wallet/ledger, available balance, application records and same-customer invoice constraints. |
| Audit / permissions | User roles, Staff modules and waiter permissions exist; generic auth, role and module middleware exist. | Note-specific audit log and permission scopes; risk action re-auth/OTP proof and immutable actor/action snapshots. |

### Source references

- Base/listeners and mounts: `server.js:1-24`; `src/app.js:60-132`.
- Notes routes/controller/model/counter: `src/routes/creditDebitNote.routes.js:1-17`; `src/controller/creditDebitNote.controller.js:1-255`; `src/models/creditDebitNote.model.js:1-69`; `src/models/creditDebitNoteCounter.model.js:1-13`; `src/utils/noteNumber.js:1-40`.
- Order routes/model/detail/list/billing: `src/routes/order.routes.js:1-115`; `src/models/order.model.js:1-379`; `src/controller/order.controller.js:1952-2075,3300-3370`.
- Print numbers: `src/controller/prints.controller.js:1-151`.
- Auth/guards: `src/middlewares/auth.middleware.js:1-115`; `src/middlewares/requireRole.middleware.js:1-14`; `src/middlewares/requireModule.middleware.js:1-29`; `src/constants/modules.js:1-17`.
- Razorpay config and booking-only refund/payment data: `src/config/razorpay.js:1-9`; `src/models/booking.model.js:1-180`; `src/controller/booking.controller.js:193-264`.

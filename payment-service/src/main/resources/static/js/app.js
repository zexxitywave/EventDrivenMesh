console.log("app.js loaded");

// ── State ──────────────────────────────────────────────────────────────────
let currentOrder = null;

// ── DOM refs ───────────────────────────────────────────────────────────────
const lookupSection   = document.getElementById("lookupSection");
const orderSection    = document.getElementById("orderSection");
const lookupError     = document.getElementById("lookupError");
const statusDiv       = document.getElementById("status");
const orderIdInput    = document.getElementById("orderIdInput");
const customerIdInput = document.getElementById("customerIdInput");
const gatewaySelect   = document.getElementById("gatewaySelect");

document.getElementById("loadOrderBtn").addEventListener("click", loadOrder);
document.getElementById("payBtn").addEventListener("click", initiatePayment);
document.getElementById("backBtn").addEventListener("click", showLookup);

// Statuses where payment is already done — Pay Now should be hidden
const TERMINAL_PAID_STATUSES = ["SHIPPED", "COMPLETED", "PAYMENT_COMPLETED"];
// Statuses where order failed — no payment possible
const TERMINAL_FAILED_STATUSES = ["FAILED", "CANCELLED"];
// Statuses where we are still waiting for inventory — too early to pay
const PENDING_SAGA_STATUSES = ["INVENTORY_CHECKING"];

// ── Step 1 — Load order ────────────────────────────────────────────────────
async function loadOrder() {
    const orderId    = orderIdInput.value.trim();
    const customerId = customerIdInput.value.trim();

    lookupError.textContent = "";

    if (!orderId || !isValidUUID(orderId)) {
        lookupError.textContent = "Please enter a valid Order ID (UUID format).";
        return;
    }
    if (!customerId || !isValidUUID(customerId)) {
        lookupError.textContent = "Please enter a valid Customer ID (UUID format).";
        return;
    }

    lookupError.textContent = "Loading order...";

    try {
        const response = await fetch(`/api/v1/payments/orders/${orderId}`);

        if (response.status === 404) {
            lookupError.textContent = "Order not found. Please check the Order ID.";
            return;
        }
        if (!response.ok) {
            lookupError.textContent = `Failed to load order (HTTP ${response.status}).`;
            return;
        }

        currentOrder = await response.json();
        currentOrder._customerId = customerId;

        lookupError.textContent = "";
        renderOrderSummary();
        showOrderSection();

    } catch (err) {
        console.error("Load order error:", err);
        lookupError.textContent = "Could not reach the order service. Make sure it is running.";
    }
}

// ── Render order summary ───────────────────────────────────────────────────
function renderOrderSummary() {
    document.getElementById("displayOrderId").textContent     = currentOrder.orderId;
    document.getElementById("displayOrderStatus").textContent = currentOrder.status;

    const container = document.getElementById("orderItems");
    container.innerHTML = "";

    if (currentOrder.items && currentOrder.items.length > 0) {
        currentOrder.items.forEach(item => {
            const lineTotal = (parseFloat(item.price) * item.quantity).toFixed(2);
            const row = document.createElement("div");
            row.className = "item-row";
            row.innerHTML = `
                <div class="item-info">
                    <span class="item-name">${escapeHtml(item.productName || "Product")}</span>
                    <span class="item-qty">× ${item.quantity}</span>
                </div>
                <span class="item-price">₹${lineTotal}</span>
            `;
            container.appendChild(row);
        });
    } else {
        container.innerHTML = `<p class="no-items">No items in this order.</p>`;
    }

    document.getElementById("orderTotal").textContent =
        `₹${parseFloat(currentOrder.totalAmount).toFixed(2)}`;

    const payBtn = document.getElementById("payBtn");
    const status = currentOrder.status;

    // ── Guard: disable Pay Now based on order status ───────────────────────
    if (TERMINAL_PAID_STATUSES.includes(status)) {
        // Already paid — hide button, show success message
        payBtn.style.display = "none";
        setStatus("✅ Payment already completed for this order.", "success");
        return;
    }

    if (TERMINAL_FAILED_STATUSES.includes(status)) {
        // Order failed/cancelled — cannot pay
        payBtn.style.display = "none";
        setStatus("❌ This order has been " + status.toLowerCase() + ". No payment is possible.", "error");
        return;
    }

    if (PENDING_SAGA_STATUSES.includes(status)) {
        // Saga still processing inventory — too early
        payBtn.disabled = true;
        payBtn.textContent = "Waiting for inventory...";
        setStatus("⏳ Inventory check in progress. Please wait a moment and reload.", "warn");
        // Auto-poll every 3 seconds until status changes
        setTimeout(async () => {
            try {
                const r = await fetch(`/api/v1/payments/orders/${currentOrder.orderId}`);
                if (r.ok) {
                    const updated = await r.json();
                    currentOrder = { ...updated, _customerId: currentOrder._customerId };
                    renderOrderSummary();
                }
            } catch (e) { /* ignore */ }
        }, 3000);
        return;
    }

    // Status is INVENTORY_RESERVED or PAYMENT_PROCESSING — enable Pay Now
    payBtn.style.display = "";
    payBtn.disabled = false;
    payBtn.textContent = "Pay Now";
    statusDiv.textContent = "";
}

// ── Step 2 — Initiate & verify payment ────────────────────────────────────
async function initiatePayment() {
    if (!currentOrder) return;

    const payBtn = document.getElementById("payBtn");
    if (payBtn.disabled) return;

    payBtn.disabled = true;
    payBtn.textContent = "Processing...";
    statusDiv.textContent = "";

    const orderId       = currentOrder.orderId;
    const customerId    = currentOrder._customerId;
    const amount        = currentOrder.totalAmount;
    const gateway       = gatewaySelect.value;
    const customerEmail = currentOrder.customerEmail || null;

    setStatus("Creating payment order...");

    try {
        const initResponse = await fetch("/api/v1/payments/initiate", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ orderId, customerId, customerEmail, amount, paymentMethod: "UPI", gateway })
        });

        if (!initResponse.ok) {
            const err = await initResponse.text();
            payBtn.disabled = false;
            payBtn.textContent = "Pay Now";

            if (initResponse.status === 409) {
                // 409 = payment already exists — check what state it's in
                let errMsg = "";
                try { errMsg = JSON.parse(err)?.message || err; } catch { errMsg = err; }

                if (errMsg.startsWith("PENDING:")) {
                    // PENDING payment exists — the Razorpay order was already created
                    // but user never completed it. Re-open with existing session.
                    const parts = errMsg.split("|");
                    const existingPaymentId = parts[0].replace("PENDING:", "");
                    setStatus("⏳ Resuming existing payment session...");

                    // Check if it was actually completed in the meantime
                    const payRes = await fetch(`/api/v1/payments/${existingPaymentId}`);
                    if (payRes.ok) {
                        const pay = await payRes.json();
                        if (pay.status === "COMPLETED") {
                            payBtn.style.display = "none";
                            setStatus("✅ Payment already completed! Transaction: " + pay.transactionId, "success");
                            return;
                        }
                    }
                    setStatus("❌ A previous payment session exists but could not be resumed. Please create a new order.", "error");
                } else {
                    // COMPLETED — already paid
                    payBtn.style.display = "none";
                    setStatus("✅ This order has already been paid successfully.", "success");
                }
            } else {
                setStatus(`❌ Failed to initiate payment (HTTP ${initResponse.status}): ${err}`, "error");
            }
            return;
        }

        const gatewayOrder = await initResponse.json();
        console.log("Gateway order:", gatewayOrder);

        // MOCK gateway — auto-verify, no popup needed
        if (gateway === "MOCK") {
            await verifyPayment(gatewayOrder, {
                razorpay_payment_id: "mock_pay_" + Date.now(),
                razorpay_order_id:   gatewayOrder.gatewayOrderId,
                razorpay_signature:  ""
            });
            return;
        }

        // Razorpay — open checkout popup
        const options = {
            key:         gatewayOrder.gatewayKey,
            amount:      Math.round(parseFloat(gatewayOrder.amount) * 100),
            currency:    gatewayOrder.currency || "INR",
            order_id:    gatewayOrder.gatewayOrderId,
            name:        "Zexxity Store",
            description: buildDescription(),
            handler: async function (paymentResponse) {
                setStatus("Verifying payment...");
                await verifyPayment(gatewayOrder, paymentResponse);
            },
            modal: {
                ondismiss: function () {
                    // User closed popup — re-enable button so they can try again
                    payBtn.disabled = false;
                    payBtn.textContent = "Pay Now";
                    setStatus("⚠️ Payment cancelled. Click Pay Now to try again.", "warn");
                }
            },
            theme: { color: "#3399cc" }
        };

        const razorpay = new Razorpay(options);
        razorpay.on("payment.failed", function (response) {
            payBtn.disabled = false;
            payBtn.textContent = "Pay Now";
            setStatus("❌ Payment failed: " + (response.error?.description || "Unknown error"), "error");
        });
        razorpay.open();

    } catch (err) {
        console.error("initiatePayment error:", err);
        payBtn.disabled = false;
        payBtn.textContent = "Pay Now";
        setStatus("❌ Unable to start payment. Check console for details.", "error");
    }
}

async function verifyPayment(gatewayOrder, razorpayResponse) {
    try {
        const verifyResponse = await fetch("/api/v1/payments/verify", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                paymentId:        gatewayOrder.paymentId,
                gatewayPaymentId: razorpayResponse.razorpay_payment_id,
                gatewayOrderId:   razorpayResponse.razorpay_order_id,
                gatewaySignature: razorpayResponse.razorpay_signature
            })
        });

        const result = await verifyResponse.json();

        if (verifyResponse.ok && result.status === "COMPLETED") {
            document.getElementById("payBtn").style.display = "none";
            setStatus(`✅ Payment successful! Transaction ID: ${result.transactionId || result.id}`, "success");
        } else {
            const payBtn = document.getElementById("payBtn");
            payBtn.disabled = false;
            payBtn.textContent = "Pay Now";
            setStatus(`❌ Payment verification failed — status: ${result.status || "UNKNOWN"}`, "error");
        }
    } catch (err) {
        console.error("verifyPayment error:", err);
        setStatus("❌ Verification failed. Check console for details.", "error");
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────
function showOrderSection() {
    lookupSection.classList.add("hidden");
    orderSection.classList.remove("hidden");
}

function showLookup() {
    orderSection.classList.add("hidden");
    lookupSection.classList.remove("hidden");
    statusDiv.textContent = "";
    document.getElementById("payBtn").style.display = "";
    document.getElementById("payBtn").disabled = false;
    document.getElementById("payBtn").textContent = "Pay Now";
}

function setStatus(msg, type) {
    statusDiv.textContent = msg;
    statusDiv.className = type || "";
}

function isValidUUID(str) {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str);
}

function escapeHtml(str) {
    return str.replace(/&/g, "&amp;")
              .replace(/</g, "&lt;")
              .replace(/>/g, "&gt;")
              .replace(/"/g, "&quot;");
}

function buildDescription() {
    if (!currentOrder?.items?.length) return "Order payment";
    const names = currentOrder.items.map(i => i.productName || "Product").join(", ");
    return names.length > 80 ? names.substring(0, 77) + "..." : names;
}

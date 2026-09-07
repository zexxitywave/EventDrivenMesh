console.log("app.js loaded");

// ── State ──────────────────────────────────────────────────────────────────
let currentOrder = null;

// ── DOM refs ───────────────────────────────────────────────────────────────
const lookupSection  = document.getElementById("lookupSection");
const orderSection   = document.getElementById("orderSection");
const lookupError    = document.getElementById("lookupError");
const statusDiv      = document.getElementById("status");

const orderIdInput   = document.getElementById("orderIdInput");
const customerIdInput = document.getElementById("customerIdInput");
const gatewaySelect  = document.getElementById("gatewaySelect");

document.getElementById("loadOrderBtn").addEventListener("click", loadOrder);
document.getElementById("payBtn").addEventListener("click", initiatePayment);
document.getElementById("backBtn").addEventListener("click", showLookup);

// ── Step 1 — Load order details ────────────────────────────────────────────
async function loadOrder() {
    const orderId    = orderIdInput.value.trim();
    const customerId = customerIdInput.value.trim();

    lookupError.textContent = "";

    if (!orderId) {
        lookupError.textContent = "Please enter an Order ID.";
        return;
    }
    if (!customerId) {
        lookupError.textContent = "Please enter a Customer ID.";
        return;
    }
    if (!isValidUUID(orderId)) {
        lookupError.textContent = "Order ID must be a valid UUID.";
        return;
    }
    if (!isValidUUID(customerId)) {
        lookupError.textContent = "Customer ID must be a valid UUID.";
        return;
    }

    lookupError.textContent = "Loading order...";

    try {
        // Proxy endpoint in payment-service — avoids CORS & JWT header issues from the browser
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
        currentOrder._customerId = customerId; // attach for payment step

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
    // Order meta
    document.getElementById("displayOrderId").textContent    = currentOrder.orderId;
    document.getElementById("displayOrderStatus").textContent = currentOrder.status;

    // Items
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

    // Total
    document.getElementById("orderTotal").textContent =
        `₹${parseFloat(currentOrder.totalAmount).toFixed(2)}`;
}

// ── Step 2 — Initiate & verify payment ────────────────────────────────────
async function initiatePayment() {
    if (!currentOrder) return;

    statusDiv.textContent = "";

    const orderId    = currentOrder.orderId;
    const customerId = currentOrder._customerId;
    const amount     = currentOrder.totalAmount;
    const gateway    = gatewaySelect.value;
    // email comes from OrderResponse now that order-service returns it
    const customerEmail = currentOrder.customerEmail || null;

    setStatus("Creating payment order...");

    try {
        const initResponse = await fetch("/api/v1/payments/initiate", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                orderId,
                customerId,
                customerEmail,
                amount,
                paymentMethod: "UPI",
                gateway
            })
        });

        if (!initResponse.ok) {
            const err = await initResponse.text();
            setStatus(`❌ Failed to initiate payment (HTTP ${initResponse.status}): ${err}`, "error");
            return;
        }

        const gatewayOrder = await initResponse.json();
        console.log("Gateway order:", gatewayOrder);

        // If MOCK gateway, no Razorpay UI — just verify directly
        if (gateway === "MOCK") {
            await verifyPayment(gatewayOrder, {
                razorpay_payment_id: "mock_pay_" + Date.now(),
                razorpay_order_id:   gatewayOrder.gatewayOrderId,
                razorpay_signature:  ""
            });
            return;
        }

        // Razorpay checkout
        const options = {
            key:      gatewayOrder.gatewayKey,
            amount:   Math.round(parseFloat(gatewayOrder.amount) * 100),
            currency: gatewayOrder.currency || "INR",
            order_id: gatewayOrder.gatewayOrderId,
            name:     "Zexxity Store",
            description: buildDescription(),

            handler: async function (paymentResponse) {
                console.log("Razorpay callback:", paymentResponse);
                setStatus("Verifying payment...");
                await verifyPayment(gatewayOrder, paymentResponse);
            },

            modal: {
                ondismiss: function () {
                    console.log("User closed Razorpay popup");
                    setStatus("⚠️ Payment cancelled", "warn");
                }
            },

            theme: { color: "#3399cc" }
        };

        console.log("Opening Razorpay checkout");
        const razorpay = new Razorpay(options);

        razorpay.on("payment.failed", function (response) {
            console.error("Razorpay payment.failed:", response.error);
            setStatus("❌ Payment failed: " + (response.error?.description || "Unknown error"), "error");
        });

        razorpay.open();

    } catch (err) {
        console.error("initiatePayment error:", err);
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
        console.log("Verification result:", result);

        if (verifyResponse.ok && result.status === "COMPLETED") {
            setStatus(
                `✅ Payment successful! Transaction ID: ${result.transactionId || result.id}`,
                "success"
            );
        } else {
            setStatus(
                `❌ Payment verification failed — status: ${result.status || "UNKNOWN"}`,
                "error"
            );
        }
    } catch (err) {
        console.error("verifyPayment error:", err);
        setStatus("❌ Verification request failed. Check console for details.", "error");
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
    if (!currentOrder || !currentOrder.items || currentOrder.items.length === 0) {
        return "Order payment";
    }
    const names = currentOrder.items.map(i => i.productName || "Product").join(", ");
    return names.length > 80 ? names.substring(0, 77) + "..." : names;
}

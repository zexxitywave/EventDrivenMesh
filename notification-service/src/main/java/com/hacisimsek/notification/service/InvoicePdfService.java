package com.hacisimsek.notification.service;

import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a styled PDF invoice from an OrderCreatedEvent using OpenPDF.
 *
 * Invoice layout:
 *   ┌─────────────────────────────────────────┐
 *   │  ZEXXITY                    INVOICE     │
 *   │  zexxity.online             #INV-XXXX   │
 *   │  noreply@zexxity.online     Date: ...   │
 *   ├─────────────────────────────────────────┤
 *   │  Bill To:                               │
 *   │  Customer ID / Email                    │
 *   ├─────────────────────────────────────────┤
 *   │  #  Product       Qty  Unit Price Total │
 *   │  1  Laptop         1   ₹999.00  ₹999   │
 *   ├─────────────────────────────────────────┤
 *   │                    Subtotal   ₹999.00   │
 *   │                    GST 18%    ₹179.82   │
 *   │                    TOTAL      ₹1178.82  │
 *   ├─────────────────────────────────────────┤
 *   │  Thank you for shopping with Zexxity!   │
 *   └─────────────────────────────────────────┘
 */
@Service
@Slf4j
public class InvoicePdfService {

    // ── Brand colours ─────────────────────────────────────────────────────────
    private static final Color BRAND_PRIMARY   = new Color(30, 64, 175);   // deep blue
    private static final Color BRAND_SECONDARY = new Color(239, 246, 255); // light blue bg
    private static final Color TABLE_HEADER_BG = new Color(30, 64, 175);
    private static final Color TABLE_ROW_ALT   = new Color(248, 250, 252);
    private static final Color TEXT_DARK       = new Color(15, 23, 42);
    private static final Color TEXT_MUTED      = new Color(100, 116, 139);
    private static final Color BORDER_COLOR    = new Color(203, 213, 225);

    private static final float GST_RATE = 0.18f;
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    /**
     * Generates the PDF bytes for an order invoice.
     *
     * @param event the OrderCreatedEvent carrying order details
     * @return PDF as byte array — ready to attach to email or stream to client
     */
    public byte[] generateInvoice(OrderCreatedEvent event) {
        log.info("[Invoice] Generating PDF for orderId={}", event.getOrderId());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, event);
            addDivider(document);
            addBillTo(document, event);
            addDivider(document);
            addItemsTable(document, event.getItems());
            addTotalsTable(document, event.getTotalAmount());
            addFooter(document);

            document.close();
            log.info("[Invoice] PDF generated successfully for orderId={}", event.getOrderId());
            return out.toByteArray();

        } catch (Exception e) {
            log.error("[Invoice] PDF generation failed for orderId={}: {}", event.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate invoice PDF for order: " + event.getOrderId(), e);
        }
    }

    // ── Header: logo + invoice meta ───────────────────────────────────────────

    private void addHeader(Document doc, OrderCreatedEvent event) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.5f, 1f});
        header.setSpacingAfter(10);

        // Left — brand name + contact
        Font brandFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 26, BRAND_PRIMARY);
        Font subFont    = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);

        PdfPCell brandCell = new PdfPCell();
        brandCell.setBorder(Rectangle.NO_BORDER);
        brandCell.addElement(new Phrase("ZEXXITY", brandFont));
        brandCell.addElement(new Phrase("zexxity.online", subFont));
        brandCell.addElement(new Phrase("noreply@zexxity.online", subFont));
        header.addCell(brandCell);

        // Right — invoice number + date
        Font invoiceTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, TEXT_DARK);
        Font metaFont         = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);
        Font metaBoldFont     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT_DARK);

        String invoiceNumber = "INV-" + event.getOrderId().toString()
                .replace("-", "").substring(0, 10).toUpperCase();

        PdfPCell metaCell = new PdfPCell();
        metaCell.setBorder(Rectangle.NO_BORDER);
        metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        metaCell.addElement(new Phrase("INVOICE", invoiceTitleFont));

        Paragraph invoiceNo = new Paragraph();
        invoiceNo.setAlignment(Element.ALIGN_RIGHT);
        invoiceNo.add(new Chunk("Invoice No: ", metaFont));
        invoiceNo.add(new Chunk(invoiceNumber, metaBoldFont));
        metaCell.addElement(invoiceNo);

        Paragraph dateLine = new Paragraph();
        dateLine.setAlignment(Element.ALIGN_RIGHT);
        dateLine.add(new Chunk("Date: ", metaFont));
        dateLine.add(new Chunk(LocalDateTime.now().format(DATE_FMT), metaBoldFont));
        metaCell.addElement(dateLine);

        Paragraph orderLine = new Paragraph();
        orderLine.setAlignment(Element.ALIGN_RIGHT);
        orderLine.add(new Chunk("Order ID: ", metaFont));
        orderLine.add(new Chunk(event.getOrderId().toString(), metaBoldFont));
        metaCell.addElement(orderLine);

        header.addCell(metaCell);
        doc.add(header);
    }

    // ── Bill To section ───────────────────────────────────────────────────────

    private void addBillTo(Document doc, OrderCreatedEvent event) throws DocumentException {
        Font sectionFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_PRIMARY);
        Font labelFont    = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);
        Font valueFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT_DARK);

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingBefore(8);
        table.setSpacingAfter(8);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BRAND_SECONDARY);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(10);

        cell.addElement(new Phrase("BILL TO", sectionFont));

        Paragraph emailLine = new Paragraph();
        emailLine.add(new Chunk("Email:  ", labelFont));
        emailLine.add(new Chunk(
                event.getCustomerEmail() != null ? event.getCustomerEmail() : "—", valueFont));
        cell.addElement(emailLine);

        Paragraph idLine = new Paragraph();
        idLine.add(new Chunk("Customer ID:  ", labelFont));
        idLine.add(new Chunk(event.getCustomerId().toString(), valueFont));
        cell.addElement(idLine);

        table.addCell(cell);
        doc.add(table);
    }

    // ── Line items table ──────────────────────────────────────────────────────

    private void addItemsTable(Document doc, List<OrderItemDto> items) throws DocumentException {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font cellFont   = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_DARK);
        Font boldCell   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT_DARK);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 3.5f, 1f, 1.5f, 1.5f});
        table.setSpacingBefore(10);
        table.setSpacingAfter(4);

        // Table header row
        String[] headers = {"#", "Product", "Qty", "Unit Price", "Total"};
        int[] alignments = {
                Element.ALIGN_CENTER,
                Element.ALIGN_LEFT,
                Element.ALIGN_CENTER,
                Element.ALIGN_RIGHT,
                Element.ALIGN_RIGHT
        };

        for (int i = 0; i < headers.length; i++) {
            PdfPCell h = new PdfPCell(new Phrase(headers[i], headerFont));
            h.setBackgroundColor(TABLE_HEADER_BG);
            h.setBorderColor(TABLE_HEADER_BG);
            h.setPadding(7);
            h.setHorizontalAlignment(alignments[i]);
            table.addCell(h);
        }

        // Data rows
        if (items == null || items.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No items", cellFont));
            empty.setColspan(5);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setPadding(8);
            table.addCell(empty);
        } else {
            for (int i = 0; i < items.size(); i++) {
                OrderItemDto item = items.get(i);
                Color rowBg = (i % 2 == 0) ? Color.WHITE : TABLE_ROW_ALT;

                BigDecimal unitPrice = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                int qty              = item.getQuantity() != null ? item.getQuantity() : 1;
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
                String name          = item.getProductName() != null
                        ? item.getProductName()
                        : item.getProductId().toString().substring(0, 8) + "...";

                String[][] rowData = {
                        {String.valueOf(i + 1), String.valueOf(Element.ALIGN_CENTER)},
                        {name,                  String.valueOf(Element.ALIGN_LEFT)},
                        {String.valueOf(qty),    String.valueOf(Element.ALIGN_CENTER)},
                        {"Rs." + unitPrice.setScale(2, RoundingMode.HALF_UP), String.valueOf(Element.ALIGN_RIGHT)},
                        {"Rs." + lineTotal.setScale(2, RoundingMode.HALF_UP), String.valueOf(Element.ALIGN_RIGHT)}
                };

                for (int[] a : new int[][]{{0, Element.ALIGN_CENTER},
                        {1, Element.ALIGN_LEFT},
                        {2, Element.ALIGN_CENTER},
                        {3, Element.ALIGN_RIGHT},
                        {4, Element.ALIGN_RIGHT}}) {
                    PdfPCell c = new PdfPCell(new Phrase(rowData[a[0]][0],
                            a[0] == 4 ? boldCell : cellFont));
                    c.setBackgroundColor(rowBg);
                    c.setBorderColor(BORDER_COLOR);
                    c.setBorderWidth(0.5f);
                    c.setPadding(6);
                    c.setHorizontalAlignment(a[1]);
                    table.addCell(c);
                }
            }
        }

        doc.add(table);
    }

    // ── Totals block ──────────────────────────────────────────────────────────

    private void addTotalsTable(Document doc, BigDecimal totalAmount) throws DocumentException {
        Font labelFont  = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);
        Font valueFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT_DARK);
        Font totalLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);
        Font totalValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

        BigDecimal amount   = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        // Back-calculate subtotal assuming totalAmount is already inclusive of GST
        BigDecimal subtotal = amount.divide(BigDecimal.valueOf(1 + GST_RATE), 2, RoundingMode.HALF_UP);
        BigDecimal gst      = amount.subtract(subtotal).setScale(2, RoundingMode.HALF_UP);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(40);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setWidths(new float[]{1.5f, 1f});
        table.setSpacingBefore(4);
        table.setSpacingAfter(20);

        addTotalRow(table, "Subtotal",  "Rs." + subtotal, labelFont, valueFont, Color.WHITE);
        addTotalRow(table, "GST (18%)", "Rs." + gst,      labelFont, valueFont, Color.WHITE);

        // Grand total row — highlighted
        PdfPCell grandLabel = new PdfPCell(new Phrase("TOTAL", totalLabel));
        grandLabel.setBackgroundColor(BRAND_PRIMARY);
        grandLabel.setBorderColor(BRAND_PRIMARY);
        grandLabel.setPadding(8);
        grandLabel.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell grandValue = new PdfPCell(
                new Phrase("Rs." + amount.setScale(2, RoundingMode.HALF_UP), totalValue));
        grandValue.setBackgroundColor(BRAND_PRIMARY);
        grandValue.setBorderColor(BRAND_PRIMARY);
        grandValue.setPadding(8);
        grandValue.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(grandLabel);
        table.addCell(grandValue);

        doc.add(table);
    }

    private void addTotalRow(PdfPTable table, String label, String value,
                              Font labelFont, Font valueFont, Color bg) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBackgroundColor(bg);
        l.setBorderColor(BORDER_COLOR);
        l.setBorderWidth(0.5f);
        l.setPadding(6);
        l.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
        v.setBackgroundColor(bg);
        v.setBorderColor(BORDER_COLOR);
        v.setBorderWidth(0.5f);
        v.setPadding(6);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(l);
        table.addCell(v);
    }

    // ── Divider line ──────────────────────────────────────────────────────────

    private void addDivider(Document doc) throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(4);
        line.setSpacingAfter(4);
        PdfPCell cell = new PdfPCell(new Phrase(" "));
        cell.setBorderWidthBottom(1f);
        cell.setBorderColorBottom(BORDER_COLOR);
        cell.setBorderWidthTop(0);
        cell.setBorderWidthLeft(0);
        cell.setBorderWidthRight(0);
        cell.setPaddingBottom(4);
        line.addCell(cell);
        doc.add(line);
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void addFooter(Document doc) throws DocumentException {
        Font footerFont  = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);
        Font thankFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_PRIMARY);

        Paragraph thank = new Paragraph("Thank you for shopping with Zexxity!", thankFont);
        thank.setAlignment(Element.ALIGN_CENTER);
        thank.setSpacingBefore(10);
        doc.add(thank);

        Paragraph note = new Paragraph(
                "This is a computer-generated invoice and does not require a physical signature.\n"
                        + "For support, contact us at support@zexxity.online",
                footerFont);
        note.setAlignment(Element.ALIGN_CENTER);
        note.setSpacingBefore(4);
        doc.add(note);
    }
}

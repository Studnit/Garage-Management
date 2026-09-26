package com.citycarservices.garage.service;

import com.citycarservices.garage.config.BusinessException;
import com.citycarservices.garage.config.Fmt;
import com.citycarservices.garage.config.GarageProperties;
import com.citycarservices.garage.domain.*;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

/** Renders a GST tax invoice as PDF (OpenPDF). Amounts use "Rs." because the base-14 fonts have no rupee glyph. */
@Service
public class InvoicePdfService {

    private static final Color DARK = new Color(0x11, 0x18, 0x27);
    private static final Color ACCENT = new Color(0xF9, 0x73, 0x16);
    private static final Color LIGHT = new Color(0xF3, 0xF4, 0xF6);

    private final GarageProperties props;
    private final Fmt fmt;

    public InvoicePdfService(GarageProperties props, Fmt fmt) {
        this.props = props;
        this.fmt = fmt;
    }

    private String rs(BigDecimal v) {
        return "Rs. " + fmt.plain(v);
    }

    private Font font(float size, boolean bold, Color color) {
        Font f = FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size);
        f.setColor(color);
        return f;
    }

    private PdfPCell cell(String text, Font font, int align, Color bg, int border) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(5);
        c.setBorder(border);
        c.setBorderColor(new Color(0xD1, 0xD5, 0xDB));
        if (bg != null) {
            c.setBackgroundColor(bg);
        }
        return c;
    }

    public byte[] render(Invoice inv) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font normal = font(9, false, Color.BLACK);
            Font bold = font(9, true, Color.BLACK);
            Font small = font(8, false, new Color(0x4B, 0x55, 0x63));

            // header
            PdfPTable head = new PdfPTable(new float[] {60, 40});
            head.setWidthPercentage(100);
            PdfPCell left = new PdfPCell();
            left.setBorder(Rectangle.NO_BORDER);
            left.addElement(new Paragraph(props.getName().toUpperCase(), font(18, true, DARK)));
            left.addElement(new Paragraph(props.getTagline(), small));
            left.addElement(new Paragraph(props.getAddress(), small));
            left.addElement(new Paragraph("Phone: " + props.getPhone()
                    + (props.getEmail().isBlank() ? "" : "   Email: " + props.getEmail()), small));
            left.addElement(new Paragraph((props.getGstin().isBlank() ? "" : "GSTIN: " + props.getGstin() + "   ")
                    + (props.getPan().isBlank() ? "" : "PAN: " + props.getPan()), small));
            head.addCell(left);
            PdfPCell right = new PdfPCell();
            right.setBorder(Rectangle.NO_BORDER);
            Paragraph title = new Paragraph("TAX INVOICE", font(16, true, ACCENT));
            title.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(title);
            Paragraph meta = new Paragraph("Invoice No: " + inv.getInvoiceNo() + "\nDate: " + fmt.date(inv.getInvoiceDate())
                    + "\nJob card: " + inv.getJobCard().getJobNo() + "\nPlace of supply: " + props.getStateName()
                    + " (" + props.getStateCode() + ")", normal);
            meta.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(meta);
            head.addCell(right);
            doc.add(head);
            doc.add(new Paragraph(" "));

            // bill to / vehicle
            Customer c = inv.getCustomer();
            Vehicle v = inv.getVehicle();
            PdfPTable info = new PdfPTable(new float[] {50, 50});
            info.setWidthPercentage(100);
            PdfPCell bill = new PdfPCell();
            bill.setPadding(6);
            bill.setBackgroundColor(LIGHT);
            bill.setBorder(Rectangle.NO_BORDER);
            bill.addElement(new Paragraph("BILL TO", font(8, true, ACCENT)));
            bill.addElement(new Paragraph(c.getName(), bold));
            if (c.getAddress() != null) {
                bill.addElement(new Paragraph(c.getAddress(), normal));
            }
            bill.addElement(new Paragraph("Mobile: " + c.getMobile(), normal));
            if (c.getGstin() != null) {
                bill.addElement(new Paragraph("GSTIN: " + c.getGstin(), normal));
            }
            info.addCell(bill);
            PdfPCell veh = new PdfPCell();
            veh.setPadding(6);
            veh.setBackgroundColor(LIGHT);
            veh.setBorder(Rectangle.NO_BORDER);
            veh.addElement(new Paragraph("VEHICLE", font(8, true, ACCENT)));
            veh.addElement(new Paragraph(v.getRegNo() + " - " + v.getDisplayName(), bold));
            veh.addElement(new Paragraph((v.getFuelType() == null ? "" : v.getFuelType().getLabel())
                    + (v.getMfgYear() == null ? "" : "  |  Year " + v.getMfgYear()), normal));
            veh.addElement(new Paragraph("Odometer: " + inv.getJobCard().getOdometerIn() + " km"
                    + (v.getVin() == null ? "" : "\nVIN: " + v.getVin()), normal));
            info.addCell(veh);
            doc.add(info);
            doc.add(new Paragraph(" "));

            // lines
            PdfPTable t = new PdfPTable(new float[] {4, 30, 9, 7, 11, 11, 7, 13});
            t.setWidthPercentage(100);
            Font th = font(8, true, Color.WHITE);
            String[] heads = {"#", "Description", "HSN/SAC", "Qty", "Rate", "Taxable", "GST%", "Amount"};
            for (int i = 0; i < heads.length; i++) {
                t.addCell(cell(heads[i], th, i >= 3 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, DARK, Rectangle.NO_BORDER));
            }
            int n = 1;
            for (InvoiceLine l : inv.getLines()) {
                int b = Rectangle.BOTTOM;
                t.addCell(cell(String.valueOf(n++), normal, Element.ALIGN_LEFT, null, b));
                t.addCell(cell(l.getDescription(), normal, Element.ALIGN_LEFT, null, b));
                t.addCell(cell(l.getHsnSac() == null ? "" : l.getHsnSac(), normal, Element.ALIGN_LEFT, null, b));
                t.addCell(cell(fmt.qty(l.getQuantity()), normal, Element.ALIGN_RIGHT, null, b));
                t.addCell(cell(fmt.plain(l.getUnitPrice()), normal, Element.ALIGN_RIGHT, null, b));
                t.addCell(cell(fmt.plain(l.getTaxableValue()), normal, Element.ALIGN_RIGHT, null, b));
                t.addCell(cell(fmt.qty(l.getGstRate()), normal, Element.ALIGN_RIGHT, null, b));
                t.addCell(cell(fmt.plain(l.getLineTotal()), normal, Element.ALIGN_RIGHT, null, b));
            }
            doc.add(t);
            doc.add(new Paragraph(" "));

            // totals
            PdfPTable tot = new PdfPTable(new float[] {58, 42});
            tot.setWidthPercentage(100);
            PdfPCell words = new PdfPCell();
            words.setBorder(Rectangle.NO_BORDER);
            words.addElement(new Paragraph("Amount in words", font(8, true, ACCENT)));
            words.addElement(new Paragraph(fmt.words(inv.getTotal()), normal));
            if (!props.getBankDetails().isBlank()) {
                words.addElement(new Paragraph(" ", small));
                words.addElement(new Paragraph("Bank details", font(8, true, ACCENT)));
                words.addElement(new Paragraph(props.getBankDetails(), normal));
            }
            if (!props.getUpiId().isBlank()) {
                words.addElement(new Paragraph("UPI: " + props.getUpiId(), normal));
            }
            tot.addCell(words);
            PdfPTable sums = new PdfPTable(new float[] {60, 40});
            addSum(sums, "Subtotal", rs(inv.getSubtotal()), normal, false);
            if (inv.getDiscount().signum() > 0) {
                addSum(sums, "Discount", "- " + rs(inv.getDiscount()), normal, false);
            }
            addSum(sums, "Taxable value", rs(inv.getTaxableValue()), normal, false);
            addSum(sums, "CGST", rs(inv.getCgst()), normal, false);
            addSum(sums, "SGST", rs(inv.getSgst()), normal, false);
            if (inv.getRoundOff().signum() != 0) {
                addSum(sums, "Round off", rs(inv.getRoundOff()), normal, false);
            }
            addSum(sums, "TOTAL", rs(inv.getTotal()), bold, true);
            addSum(sums, "Paid", rs(inv.getAmountPaid()), normal, false);
            addSum(sums, "Balance due", rs(inv.getBalance()), bold, false);
            PdfPCell sumCell = new PdfPCell(sums);
            sumCell.setBorder(Rectangle.NO_BORDER);
            tot.addCell(sumCell);
            doc.add(tot);

            Paragraph status = new Paragraph("Payment status: " + inv.getPaymentStatus().getLabel(),
                    font(9, true, inv.isPaid() ? new Color(0x16, 0xA3, 0x4A) : new Color(0xDC, 0x26, 0x26)));
            status.setSpacingBefore(8);
            doc.add(status);
            if (inv.getNotes() != null) {
                doc.add(new Paragraph("Notes: " + inv.getNotes(), small));
            }
            Paragraph terms = new Paragraph("\nTerms: " + props.getInvoiceTerms()
                    + "\nThis is a computer generated invoice. Thank you for choosing " + props.getName() + ".", small);
            doc.add(terms);
            Paragraph sign = new Paragraph("\n\nFor " + props.getName() + "\n\n\nAuthorised signatory", bold);
            sign.setAlignment(Element.ALIGN_RIGHT);
            doc.add(sign);

            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new BusinessException("Could not generate the PDF: " + e.getMessage());
        }
    }

    private void addSum(PdfPTable t, String label, String value, Font font, boolean highlight) {
        Color bg = highlight ? LIGHT : null;
        t.addCell(cell(label, font, Element.ALIGN_LEFT, bg, Rectangle.NO_BORDER));
        t.addCell(cell(value, font, Element.ALIGN_RIGHT, bg, Rectangle.NO_BORDER));
    }
}

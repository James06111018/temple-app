package tw.org.il.dongsheng.templeapp.model;

import java.util.Objects;

public class Donation {
    private Integer id;
    private Integer memberId;
    private String receiptNo;
    private String donateDate;
    private String extraNo;
    private Integer amount;
    private String summary;
    private String donateNote;
    private String otherNote;
    private String donorNo;
    private String lightNo;
    private Integer shouldPay;
    private String donateType;
    private String creator;

    public Donation() {
    }

    public Donation(
            Integer id,
            Integer memberId,
            String receiptNo,
            String donateDate,
            String extraNo,
            Integer amount,
            String summary,
            String donateNote,
            String otherNote,
            String donorNo,
            String lightNo,
            Integer shouldPay,
            String donateType,
            String creator
    ) {
        this.id = id;
        this.memberId = memberId;
        this.receiptNo = receiptNo;
        this.donateDate = donateDate;
        this.extraNo = extraNo;
        this.amount = amount;
        this.summary = summary;
        this.donateNote = donateNote;
        this.otherNote = otherNote;
        this.donorNo = donorNo;
        this.lightNo = lightNo;
        this.shouldPay = shouldPay;
        this.donateType = donateType;
        this.creator = creator;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMemberId() {
        return memberId;
    }

    public void setMemberId(Integer memberId) {
        this.memberId = memberId;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public String getDonateDate() {
        return donateDate;
    }

    public void setDonateDate(String donateDate) {
        this.donateDate = donateDate;
    }

    public String getExtraNo() {
        return extraNo;
    }

    public void setExtraNo(String extraNo) {
        this.extraNo = extraNo;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDonateNote() {
        return donateNote;
    }

    public void setDonateNote(String donateNote) {
        this.donateNote = donateNote;
    }

    public String getOtherNote() {
        return otherNote;
    }

    public void setOtherNote(String otherNote) {
        this.otherNote = otherNote;
    }

    public String getDonorNo() {
        return donorNo;
    }

    public void setDonorNo(String donorNo) {
        this.donorNo = donorNo;
    }

    public String getLightNo() {
        return lightNo;
    }

    public void setLightNo(String lightNo) {
        this.lightNo = lightNo;
    }

    public Integer getShouldPay() {
        return shouldPay;
    }

    public void setShouldPay(Integer shouldPay) {
        this.shouldPay = shouldPay;
    }

    public String getDonateType() {
        return donateType;
    }

    public void setDonateType(String donateType) {
        this.donateType = donateType;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Donation)) {
            return false;
        }
        Donation donation = (Donation) o;
        return Objects.equals(id, donation.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Donation{" +
                "id=" + id +
                ", memberId=" + memberId +
                ", receiptNo='" + receiptNo + '\'' +
                ", donateDate='" + donateDate + '\'' +
                ", extraNo='" + extraNo + '\'' +
                ", amount=" + amount +
                ", summary='" + summary + '\'' +
                ", donateNote='" + donateNote + '\'' +
                ", otherNote='" + otherNote + '\'' +
                ", donorNo='" + donorNo + '\'' +
                ", lightNo='" + lightNo + '\'' +
                ", shouldPay=" + shouldPay +
                ", donateType='" + donateType + '\'' +
                '}';
    }
}


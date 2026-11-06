package com.sky.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.common.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChannelFinanceTest {
  private static final String HEADER = "商户订单号,交易状态,商户退款单号,手续费,订单金额,应结订单金额,申请退款金额,退款金额\n";

  @Test
  void readsBomTicksMinorUnitsAndSignedFeesAndStopsBeforeSummaryValues() throws Exception {
    var rows =
        ChannelFinance.parse(
            "\uFEFF"
                + HEADER
                + "`ORDER_1,`SUCCESS,`0,`0.07,`12.34,`12.34,`0.00,`0.00\n"
                + "`ORDER_1,`REFUND,`REFUND_1,`-0.02,`12.34,`12.34,`3.21,`3.21\n"
                + "总交易单数,应结订单总金额,退款总金额,手续费总金额\n"
                + "`2,`12.34,`3.21,`0.05\n");
    assertEquals(
        List.of(
            new ChannelFinance.BillLine("ORDER_1", "PAYMENT", 1234, 7),
            new ChannelFinance.BillLine("REFUND_1", "REFUND", 321, -2)),
        rows);
  }

  @Test
  void comparesOriginalOrderAmountAndRequestedRefundInsteadOfChannelSettlementAmounts()
      throws Exception {
    var rows =
        ChannelFinance.parse(
            HEADER
                + "`ORDER_1,`SUCCESS,`0,`0.06,`10.00,`9.00,`0.00,`0.00\n"
                + "`ORDER_1,`REFUND,`REFUND_1,`-0.03,`10.00,`9.00,`5.00,`4.50\n");
    assertEquals(1000, rows.getFirst().cents());
    assertEquals(500, rows.getLast().cents());
  }

  @Test
  void supportsOlderAmountColumnsWithoutDiscardingActualAmounts() throws Exception {
    var rows =
        ChannelFinance.parse(
            "商户订单号,交易状态,商户退款单号,手续费,应结订单金额,退款金额\n" + "`ORDER_OLD,`SUCCESS,`0,`0.01,`2.50,`0.00\n");
    assertEquals(250, rows.getFirst().cents());
  }

  @Test
  void rejectsMissingRequiredColumnsAndUnknownTradeState() {
    assertFormat("商户订单号,交易状态\nORDER_1,SUCCESS\n");
    assertFormat(HEADER + "ORDER_1,USERPAYING,0,0.01,2.00,2.00,0.00,0.00\n");
  }

  @Test
  void rejectsMalformedAmountsRatherThanRoundingOrProducingAnInternalError() {
    assertFormat(HEADER + "ORDER_1,SUCCESS,0,0.01,1.001,1.001,0.00,0.00\n");
    assertFormat(HEADER + "ORDER_1,SUCCESS,0,0.01,NaN,NaN,0.00,0.00\n");
    assertFormat(HEADER + "ORDER_1,SUCCESS,0,0.001,1.00,1.00,0.00,0.00\n");
    assertFormat(HEADER + "ORDER_1,SUCCESS,0,0.01,-1.00,-1.00,0.00,0.00\n");
  }

  @Test
  void rejectsMissingChannelReferences() {
    assertFormat(HEADER + "`,SUCCESS,0,0.01,1.00,1.00,0.00,0.00\n");
    assertFormat(HEADER + "ORDER_1,REFUND,`,0.00,1.00,1.00,1.00,1.00\n");
  }

  private static void assertFormat(String csv) {
    var error = assertThrows(BusinessException.class, () -> ChannelFinance.parse(csv));
    assertEquals(502, error.status());
    assertEquals("BILL_FORMAT", error.code());
  }
}

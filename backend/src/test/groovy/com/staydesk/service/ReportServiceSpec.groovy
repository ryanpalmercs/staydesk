package com.staydesk.service

import com.staydesk.model.TerminalTransaction
import com.staydesk.model.reporting.TerminalTransactionReportRow
import com.staydesk.repository.ReportRepository
import org.thymeleaf.TemplateEngine
import spock.lang.Specification

import java.time.LocalDate

class ReportServiceSpec extends Specification {

    ReportRepository reportRepository = Mock()
    TemplateEngine templateEngine = Mock()

    ReportService reportService = new ReportService(reportRepository, templateEngine)

    def "terminal transaction count and volume are derived from the breakdown - volume counts only COMPLETED rows"() {
        given:
        def startDate = LocalDate.of(2026, 1, 1)
        def endDate = LocalDate.of(2026, 1, 31)

        reportRepository.getTotalRevenue(_, _) >> BigDecimal.ZERO
        reportRepository.getTotalTax(_, _) >> BigDecimal.ZERO
        reportRepository.getTotalRoomCount() >> 27
        reportRepository.getByRoom(_, _) >> []
        reportRepository.getGuestCountBreakdown(_, _) >> []
        reportRepository.getTerminalTransactionBreakdown(startDate, endDate) >> [
                new TerminalTransactionReportRow(TerminalTransaction.Operation.SALE, TerminalTransaction.Status.COMPLETED, 3, BigDecimal.valueOf(150)),
                new TerminalTransactionReportRow(TerminalTransaction.Operation.SALE, TerminalTransaction.Status.FAILED, 2, BigDecimal.valueOf(80)),
                new TerminalTransactionReportRow(TerminalTransaction.Operation.VOID, TerminalTransaction.Status.COMPLETED, 1, BigDecimal.ZERO),
        ]

        when:
        def summary = reportService.getReportSummary(startDate, endDate, startDate, endDate)

        then:
        summary.terminalTransactionCount() == 6
        summary.terminalTransactionVolume().compareTo(BigDecimal.valueOf(150)) == 0
        summary.terminalTransactionBreakdown().size() == 3
    }

    def "terminal transaction stats are zero when there's nothing in range"() {
        given:
        def startDate = LocalDate.of(2026, 1, 1)
        def endDate = LocalDate.of(2026, 1, 31)

        reportRepository.getTotalRevenue(_, _) >> BigDecimal.ZERO
        reportRepository.getTotalTax(_, _) >> BigDecimal.ZERO
        reportRepository.getTotalRoomCount() >> 27
        reportRepository.getByRoom(_, _) >> []
        reportRepository.getGuestCountBreakdown(_, _) >> []
        reportRepository.getTerminalTransactionBreakdown(_, _) >> []

        when:
        def summary = reportService.getReportSummary(startDate, endDate, startDate, endDate)

        then:
        summary.terminalTransactionCount() == 0
        summary.terminalTransactionVolume().compareTo(BigDecimal.ZERO) == 0
        summary.terminalTransactionBreakdown().isEmpty()
    }
}

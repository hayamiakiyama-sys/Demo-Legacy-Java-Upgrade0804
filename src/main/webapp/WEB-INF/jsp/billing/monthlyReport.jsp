<%@ page session="false" trimDirectiveWhitespaces="true" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="petclinic" tagdir="/WEB-INF/tags" %>

<petclinic:layout pageName="billing">
    <h2>Monthly closing / 月次締め</h2>

    <form class="form-inline" action="<spring:url value="/billing/monthly" htmlEscape="true"/>" method="get">
        <label for="period">Period (yyyy/MM)</label>
        <input class="form-control" type="text" id="period" name="period" value="${period}"/>
        <button class="btn btn-default" type="submit">Recalculate</button>
    </form>

    <form action="<spring:url value="/billing/monthly/export" htmlEscape="true"/>" method="post">
        <input type="hidden" name="period" value="${period}"/>
        <button class="btn btn-default" type="submit">Export CSV for accounting</button>
    </form>

    <c:if test="${not empty exportedFile}">
        <p>Exported: <c:out value="${exportedFile}"/></p>
    </c:if>

    <c:choose>
        <c:when test="${empty invoices}">
            <p>No chargeable visits in <c:out value="${period}"/>.</p>
        </c:when>
        <c:otherwise>
            <c:forEach items="${invoices}" var="invoice">
                <h3><c:out value="${invoice.ownerName}"/></h3>
                <table class="table table-striped">
                    <thead>
                    <tr>
                        <th>Visit date</th>
                        <th>Pet</th>
                        <th>Type</th>
                        <th>Description</th>
                        <th>Unit price</th>
                        <th>Holiday surcharge</th>
                        <th>Discount</th>
                        <th>Amount</th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${invoice.lines}" var="line">
                        <tr>
                            <td><fmt:formatDate value="${line.visitDate}" pattern="yyyy/MM/dd"/></td>
                            <td><c:out value="${line.petName}"/></td>
                            <td><c:out value="${line.petType}"/></td>
                            <td><c:out value="${line.description}"/></td>
                            <td><fmt:formatNumber value="${line.unitPrice}"/></td>
                            <td><fmt:formatNumber value="${line.surcharge}"/></td>
                            <td><fmt:formatNumber value="${line.discount}"/></td>
                            <td><fmt:formatNumber value="${line.amount}"/></td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
                <p>
                    Subtotal <fmt:formatNumber value="${invoice.subtotal}"/> /
                    Tax <fmt:formatNumber value="${invoice.tax}"/> /
                    <strong>Total <fmt:formatNumber value="${invoice.total}"/></strong>
                </p>
            </c:forEach>

            <h3>Grand total: <fmt:formatNumber value="${grandTotal}"/></h3>
        </c:otherwise>
    </c:choose>
</petclinic:layout>

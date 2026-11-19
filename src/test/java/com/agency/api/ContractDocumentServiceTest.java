package com.agency.api;

import com.agency.api.repository.ContractRevisionRepository;
import com.agency.api.service.ContractDocumentService;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ContractDocumentServiceTest {
    @Test void createsPdfWithHeaderAndEndMarker(){var service=new ContractDocumentService(mock(ContractRevisionRepository.class));String pdf=new String(service.renderPdf("Contrato", "Cliente: Mário\nSalário: 10.000 MZN"),StandardCharsets.ISO_8859_1);assertTrue(pdf.startsWith("%PDF-1.4"));assertTrue(pdf.contains("/Type /Page"));assertTrue(pdf.endsWith("%%EOF"));}
}

package com.agency.api.service;

import com.agency.api.domain.*;
import com.agency.api.repository.ContractRevisionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

@Service
public class ContractDocumentService {

    private final ContractRevisionRepository revisions;

    public ContractDocumentService(ContractRevisionRepository revisions) {
        this.revisions = revisions;
    }

    @Transactional
    public void ensureInitialVersion(EmploymentContract contract) {
        if (revisions.findFirstByContractIdOrderByRevisionNumberDesc(contract.getId()).isEmpty()) {
            revisions.save(new ContractRevision(contract, null, 1, "CONTRATO", contract.getContractText(), "Minuta inicial"));
        }
    }

    @Transactional
    public ContractRevision addVersion(EmploymentContract contract, Account actor, String content, String reason, boolean addendum) {
        int next = revisions.findFirstByContractIdOrderByRevisionNumberDesc(contract.getId()).map(r -> r.getRevisionNumber() + 1).orElse(1);
        return revisions.save(new ContractRevision(contract, actor, next, addendum ? "ADENDA" : "REVISAO", content, reason));
    }

    public List<ContractRevision> history(UUID contractId) {
        return revisions.findByContractIdOrderByRevisionNumberDesc(contractId);
    }

    public byte[] pdf(EmploymentContract contract) {
        String text = revisions.findFirstByContractIdOrderByRevisionNumberDesc(contract.getId()).map(ContractRevision::getContent).orElse(contract.getContractText());
        return renderPdf("Contrato LarLink", text);
    }

    public byte[] renderPdf(String title, String text) {
        try {
            String normalized = Normalizer.normalize(title + "\n\n" + text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7E\\n]", "?");
            List<String> lines = new ArrayList<>();
            for (String raw : normalized.split("\\R", -1)) {
                String line = raw;
                while (line.length() > 92) {
                    lines.add(line.substring(0, 92));
                    line = line.substring(92);
                }
                lines.add(line);
            }
            List<List<String>> pages = new ArrayList<>();
            for (int i = 0; i < lines.size(); i += 52) {
                pages.add(lines.subList(i, Math.min(i + 52, lines.size())));
            }
            if (pages.isEmpty()) {
                pages.add(List.of(""));
            }
            int count = 3 + pages.size() * 2;
            List<byte[]> objects = new ArrayList<>(Collections.nCopies(count + 1, null));
            objects.set(1, ascii("<< /Type /Catalog /Pages 2 0 R >>"));
            String kids = java.util.stream.IntStream.range(0, pages.size()).mapToObj(i -> (4 + i * 2) + " 0 R").reduce((a, b) -> a + " " + b).orElse("");
            objects.set(2, ascii("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>"));
            objects.set(3, ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
            for (int i = 0; i < pages.size(); i++) {
                int pageId = 4 + i * 2, streamId = pageId + 1;
                StringBuilder stream = new StringBuilder("BT /F1 10 Tf 48 790 Td 14 TL\n");
                for (String line : pages.get(i)) {
                    stream.append('(').append(escape(line)).append(") Tj T*\n");
                }
                stream.append("ET\n");
                byte[] bytes = stream.toString().getBytes(StandardCharsets.ISO_8859_1);
                objects.set(pageId, ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 3 0 R >> >> /Contents " + streamId + " 0 R >>"));
                objects.set(streamId, concat(ascii("<< /Length " + bytes.length + " >>\nstream\n"), bytes, ascii("\nendstream")));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(ascii("%PDF-1.4\n%LarLink\n"));
            long[] offsets = new long[count + 1];
            for (int i = 1; i <= count; i++) {
                offsets[i] = out.size();
                out.write(ascii(i + " 0 obj\n"));
                out.write(objects.get(i));
                out.write(ascii("\nendobj\n"));
            }
            long xref = out.size();
            out.write(ascii("xref\n0 " + (count + 1) + "\n0000000000 65535 f \n"));
            for (int i = 1; i <= count; i++) {
                out.write(ascii(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i])));
            }
            out.write(ascii("trailer\n<< /Size " + (count + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF"));
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gerar o PDF.", e);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... arrays) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] a : arrays) {
            out.write(a);
        }
        return out.toByteArray();
    }
}

package com.agency.api.config;

import java.util.List;
import java.util.Set;

public final class DemoHousekeeperCatalog {
    private DemoHousekeeperCatalog() {}

    public record Sample(String name, String email, String phone, String city, String district, String neighborhood,
                         Set<String> skills, Set<String> days, int salary, int experience, int birthYear) {}

    private static Set<String> skills(String... values) { return Set.of(values); }
    private static Set<String> days(String... values) { return Set.of(values); }

    public static String emailFor(String name) {
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z ]", "")
                .trim();
        String[] parts = normalized.split("\\s+");
        if (parts.length < 2) throw new IllegalArgumentException("O nome demo precisa de nome e apelido.");
        return (parts[0] + parts[parts.length - 1] + "@gmail.com").toLowerCase(java.util.Locale.ROOT);
    }

    public static List<Sample> samples() {
        return List.of(
                new Sample("Amélia Mucavele", "demo.amelia@larlink.invalid", "840000101", "Maputo", "KaMpfumu", "Polana", skills("CLEANING", "LAUNDRY"), days("MON", "WED", "FRI"), 8500, 5, 1992),
                new Sample("Celina Cossa", "demo.celina@larlink.invalid", "840000102", "Maputo", "KaMaxaqueni", "Maxaquene", skills("COOKING", "CLEANING"), days("MON", "TUE", "THU", "FRI"), 9500, 8, 1988),
                new Sample("Graça Bila", "demo.graca@larlink.invalid", "840000106", "Maputo", "KaMpfumu", "Sommerschield", skills("COOKING", "LAUNDRY", "CLEANING"), days("MON", "TUE", "WED", "THU", "FRI"), 14000, 12, 1982),
                new Sample("Joana Matavele", "demo.joana@larlink.invalid", "840000109", "Maputo", "KaMavota", "Costa do Sol", skills("CHILDCARE", "COOKING", "CLEANING"), days("MON", "TUE", "WED", "THU", "FRI", "SAT"), 13000, 7, 1990),
                new Sample("Marta Muianga", "demo.marta@larlink.invalid", "840000110", "Maputo", "KaMubukwana", "Zimpeto", skills("ELDERLY_CARE", "CLEANING", "LAUNDRY"), days("MON", "WED", "FRI", "SUN"), 10500, 11, 1985),
                new Sample("Noémia Chongo", "demo.noemia@larlink.invalid", "840000111", "Maputo", "Nhlamankulu", "Chamanculo", skills("CLEANING", "LAUNDRY", "COOKING"), days("TUE", "THU", "SAT"), 7500, 3, 1997),
                new Sample("Olga Massango", "demo.olga@larlink.invalid", "840000112", "Maputo", "KaTembe", "Incassane", skills("GARDENING", "CLEANING"), days("MON", "TUE", "WED", "THU", "FRI"), 7000, 6, 1993),
                new Sample("Paula Mondlane", "demo.paula@larlink.invalid", "840000113", "Maputo", "KaMpfumu", "Malhangalene", skills("CLEANING", "CHILDCARE", "LAUNDRY", "COOKING"), days("MON", "TUE", "WED", "THU", "FRI"), 11500, 9, 1987),

                new Sample("Dina Macuácua", "demo.dina@larlink.invalid", "840000103", "Matola", "Matola", "Matola A", skills("CHILDCARE", "CLEANING"), days("MON", "TUE", "WED", "THU", "FRI"), 11000, 6, 1995),
                new Sample("Ema Nhantumbo", "demo.ema@larlink.invalid", "840000104", "Matola", "Matola", "Machava", skills("CLEANING", "ELDERLY_CARE", "COOKING"), days("TUE", "THU", "SAT"), 12000, 10, 1984),
                new Sample("Rosa Mabunda", "demo.rosa@larlink.invalid", "840000108", "Matola", "Matola", "Fomento", skills("CLEANING", "COOKING", "ELDERLY_CARE"), days("MON", "TUE", "WED", "THU", "FRI", "SAT"), 12500, 9, 1987),
                new Sample("Sara Machel", "demo.sara@larlink.invalid", "840000114", "Matola", "Boane", "Matola-Rio", skills("CLEANING", "LAUNDRY", "GARDENING"), days("MON", "WED", "FRI", "SAT"), 9000, 4, 1996),
                new Sample("Teresa Sumbana", "demo.teresa@larlink.invalid", "840000115", "Matola", "Marracuene", "Michafutene", skills("CHILDCARE", "COOKING", "CLEANING"), days("MON", "TUE", "WED", "THU", "FRI", "SUN"), 13500, 13, 1981),
                new Sample("Úrsula Baloi", "demo.ursula@larlink.invalid", "840000116", "Matola", "Moamba", "Pessene", skills("ELDERLY_CARE", "CLEANING"), days("MON", "TUE", "THU", "SAT"), 8000, 5, 1991),

                new Sample("Fátima Sitoe", "demo.fatima@larlink.invalid", "840000105", "Beira", "Cidade da Beira", "Ponta-Gêa", skills("CLEANING", "GARDENING"), days("MON", "WED", "SAT"), 8000, 4, 1998),
                new Sample("Vitória Jamal", "demo.vitoria@larlink.invalid", "840000117", "Beira", "Cidade da Beira", "Macuti", skills("COOKING", "CLEANING", "LAUNDRY"), days("MON", "TUE", "WED", "THU", "FRI"), 10000, 8, 1989),
                new Sample("Zélia Nhampossa", "demo.zelia@larlink.invalid", "840000118", "Beira", "Dondo", "Mafarinha", skills("CHILDCARE", "CLEANING"), days("TUE", "WED", "THU", "FRI", "SAT"), 8500, 6, 1994),
                new Sample("Alzira Cuambe", "demo.alzira@larlink.invalid", "840000119", "Beira", "Nhamatanda", "Tica", skills("ELDERLY_CARE", "COOKING", "CLEANING"), days("MON", "WED", "FRI", "SUN"), 9000, 12, 1983),

                new Sample("Lídia Tembe", "demo.lidia@larlink.invalid", "840000107", "Nampula", "Cidade de Nampula", "Napipine", skills("CLEANING", "CHILDCARE", "LAUNDRY"), days("MON", "TUE", "FRI", "SAT"), 9000, 3, 2000),
                new Sample("Berta Issufo", "demo.berta@larlink.invalid", "840000120", "Nampula", "Cidade de Nampula", "Muhala", skills("CLEANING", "COOKING", "LAUNDRY"), days("MON", "TUE", "WED", "THU", "FRI"), 10500, 7, 1992),
                new Sample("Carla Momade", "demo.carla@larlink.invalid", "840000121", "Nampula", "Nacala", "Maiaia", skills("CHILDCARE", "CLEANING", "COOKING"), days("MON", "WED", "THU", "SAT", "SUN"), 9500, 5, 1995),
                new Sample("Deolinda Amisse", "demo.deolinda@larlink.invalid", "840000122", "Nampula", "Angoche", "Inguri", skills("ELDERLY_CARE", "LAUNDRY", "CLEANING"), days("TUE", "THU", "FRI", "SAT"), 7500, 9, 1986),

                new Sample("Ermelinda Chauque", "demo.ermelinda@larlink.invalid", "840000123", "Xai-Xai", "Cidade de Xai-Xai", "Patrice Lumumba", skills("CLEANING", "COOKING"), days("MON", "TUE", "WED", "THU", "FRI"), 8000, 6, 1991),
                new Sample("Fernanda Macamo", "demo.fernanda@larlink.invalid", "840000124", "Xai-Xai", "Chokwe", "Lionde", skills("GARDENING", "CLEANING", "LAUNDRY"), days("MON", "WED", "FRI", "SAT"), 7000, 4, 1997),
                new Sample("Helena Mahumane", "demo.helena@larlink.invalid", "840000125", "Xai-Xai", "Bilene", "Macia", skills("CHILDCARE", "COOKING", "CLEANING"), days("TUE", "WED", "THU", "FRI", "SUN"), 9500, 10, 1985),

                new Sample("Inês Mussa", "demo.ines@larlink.invalid", "840000126", "Inhambane", "Cidade de Inhambane", "Chalambe", skills("CLEANING", "LAUNDRY", "COOKING"), days("MON", "TUE", "WED", "THU", "FRI"), 9000, 8, 1988),
                new Sample("Julieta Cumbane", "demo.julieta@larlink.invalid", "840000127", "Inhambane", "Maxixe", "Rumbana", skills("ELDERLY_CARE", "CLEANING", "COOKING"), days("MON", "WED", "FRI", "SAT", "SUN"), 10000, 11, 1982),
                new Sample("Lurdes Mondlane", "demo.lurdes@larlink.invalid", "840000128", "Inhambane", "Vilankulo", "Central", skills("CHILDCARE", "CLEANING", "LAUNDRY"), days("TUE", "THU", "FRI", "SAT"), 8500, 5, 1994),

                new Sample("Madalena Saíde", "demo.madalena@larlink.invalid", "840000129", "Tete", "Cidade de Tete", "Matundo", skills("CLEANING", "COOKING", "LAUNDRY"), days("MON", "TUE", "WED", "THU", "FRI"), 9500, 7, 1990),
                new Sample("Natália Zimba", "demo.natalia@larlink.invalid", "840000130", "Tete", "Moatize", "Bagamoio", skills("GARDENING", "CLEANING", "ELDERLY_CARE"), days("MON", "WED", "FRI", "SAT"), 8000, 9, 1986),
                new Sample("Odete Manjate", "demo.odete@larlink.invalid", "840000131", "Manica", "Cidade de Chimoio", "Vila Nova", skills("CHILDCARE", "COOKING", "CLEANING"), days("MON", "TUE", "THU", "FRI", "SUN"), 8500, 6, 1993),
                new Sample("Quitéria Luís", "demo.quiteria@larlink.invalid", "840000132", "Zambézia", "Cidade de Quelimane", "Coalane", skills("CLEANING", "LAUNDRY", "ELDERLY_CARE"), days("TUE", "WED", "THU", "SAT"), 7500, 10, 1984),
                new Sample("Raquel António", "demo.raquel@larlink.invalid", "840000133", "Cabo Delgado", "Cidade de Pemba", "Paquitequete", skills("COOKING", "CLEANING", "CHILDCARE"), days("MON", "TUE", "WED", "THU", "FRI", "SAT"), 10000, 8, 1989),
                new Sample("Sofia Zacarias", "demo.sofia@larlink.invalid", "840000134", "Niassa", "Cidade de Lichinga", "Chiuaula", skills("CLEANING", "LAUNDRY", "GARDENING"), days("MON", "WED", "FRI", "SUN"), 7000, 4, 1996)
        );
    }
}

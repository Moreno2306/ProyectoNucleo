package horarios;

import horarios.Model.*;
import java.util.*;
import java.util.function.Predicate;

/** Dataset sintético del Anexo D (M1) y generador de escala ×k para pruebas de escalabilidad. */
public final class Datos {
    private Datos() {}

    static Predicate<Bloque> siempre = b -> true;
    static Predicate<Bloque> manana = b -> b.fin() <= 13;
    static Predicate<Bloque> despues(int h) { return b -> b.inicio() >= h; }
    static Predicate<Bloque> noDia(int d) { return b -> b.dia() != d; }
    static Predicate<Bloque> soloDias(int... ds) { return b -> { for (int d : ds) if (b.dia() == d) return true; return false; }; }

    public record Dataset(List<Docente> docentes, List<Aula> aulas, List<Curso> cursos) {}

    public static Dataset anexoD() {
        List<Aula> aulas = List.of(
            new Aula("A101", 44, Set.of("sillas")), new Aula("A102", 40, Set.of()), new Aula("A201", 30, Set.of("sillas")),
            new Aula("L301", 25, Set.of("pc")), new Aula("L302", 35, Set.of("pc")));
        List<Curso> cursos = List.of(
            c("C01",2,Set.of(),70), c("C02",2,Set.of(),38), c("C03",2,Set.of(),45), c("C04",2,Set.of("pc"),60), c("C05",1,Set.of("sillas"),42),
            c("C06",3,Set.of(),36), c("C07",2,Set.of(),34), c("C08",2,Set.of(),40), c("C09",1,Set.of(),30), c("C10",2,Set.of("pc"),48),
            c("C11",3,Set.of(),32), c("C12",2,Set.of(),35), c("C13",2,Set.of(),33), c("C14",2,Set.of("pc"),41), c("C15",2,Set.of("pc"),39),
            c("C16",2,Set.of("pc"),24), c("C17",2,Set.of("pc"),26), c("C18",2,Set.of("pc"),22), c("C19",1,Set.of("sillas"),20), c("C20",2,Set.of("sillas"),28));
        List<Docente> docentes = List.of(
            new Docente("D01", Dedicacion.TC, Set.of("C01","C02","C03","C06"), siempre),
            new Docente("D02", Dedicacion.TC, Set.of("C04","C10","C14","C15"), siempre),
            new Docente("D03", Dedicacion.TC, Set.of("C05","C11","C12"), manana),
            new Docente("D04", Dedicacion.TRES_CUARTOS, Set.of("C07","C08","C13"), siempre),
            new Docente("D05", Dedicacion.TRES_CUARTOS, Set.of("C16","C17"), despues(16)),
            new Docente("D06", Dedicacion.MEDIO, Set.of("C18","C19"), despues(18)),
            new Docente("D07", Dedicacion.MEDIO, Set.of("C09","C20"), noDia(5)),
            new Docente("D08", Dedicacion.CUARTO, Set.of("C01","C06"), soloDias(0,2)),
            new Docente("D09", Dedicacion.CUARTO, Set.of("C04","C15"), despues(16)),
            new Docente("D10", Dedicacion.TC, Set.of("C02","C03","C14","C20"), noDia(4)));
        return new Dataset(docentes, aulas, cursos);
    }

    static Curso c(String id, int s, Set<String> req, int dem) { return new Curso(id, s, req, dem); }

    /** Escala ×k clonando docentes, aulas y cursos con sufijo; las habilitaciones se clonan dentro de cada réplica. */
    public static Dataset escalar(Dataset base, int k) {
        List<Docente> ds = new ArrayList<>(); List<Aula> as = new ArrayList<>(); List<Curso> cs = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            String suf = i == 0 ? "" : "_" + i;
            for (Aula a : base.aulas()) as.add(new Aula(a.codigo() + suf, a.capacidad(), a.caracteristicas()));
            for (Curso c : base.cursos()) cs.add(new Curso(c.codigo() + suf, c.sesionesSemana(), c.aulaRequerida(), c.demanda()));
            for (Docente d : base.docentes()) {
                Set<String> hab = new HashSet<>(); for (String h : d.cursosHabilitados()) hab.add(h + suf);
                ds.add(new Docente(d.codigo() + suf, d.dedicacion(), hab, d.disponible()));
            }
        }
        return new Dataset(ds, as, cs);
    }
}

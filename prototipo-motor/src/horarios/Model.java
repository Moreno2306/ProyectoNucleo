package horarios;

import java.util.*;

/** Modelo mínimo del dominio (Anexo D del M1). */
public final class Model {
    private Model() {}

    public enum Dedicacion { TC(40), TRES_CUARTOS(30), MEDIO(20), CUARTO(10);
        public final int horas; Dedicacion(int h) { horas = h; } }

    public record Bloque(int dia, int inicio) {            // dia 0=lun..5=sab, inicio en hora (7,9,...)
        public int fin() { return inicio + 2; }
        @Override public String toString() { return "D" + dia + "@" + inicio + "h"; }
    }

    public record Aula(String codigo, int capacidad, Set<String> caracteristicas) {}

    public record Docente(String codigo, Dedicacion dedicacion, Set<String> cursosHabilitados,
                          java.util.function.Predicate<Bloque> disponible) {}

    public record Curso(String codigo, int sesionesSemana, Set<String> aulaRequerida, int demanda) {}

    /** Una sesión concreta a ubicar: grupo g del curso c, sesión k de n. */
    public record Sesion(String id, Curso curso, int grupo, int indice, int tamano) {}

    public record Asignacion(Sesion sesion, Bloque bloque, Aula aula, Docente docente) {}

    /** Parámetros institucionales (catálogo P-01..P-11 del M1). */
    public record Parametros(int tope, double tolerancia, int minimoGrupo, double fraccionDocencia,
                             int horaInicioLV, int horaFinLV, int horaFinSab, int almuerzoInicio) {
        public static Parametros porDefecto() { return new Parametros(40, 0.10, 10, 0.50, 7, 22, 13, 12); }
        public int topeEfectivo() { return (int) Math.floor(tope * (1 + tolerancia)); }
    }
}

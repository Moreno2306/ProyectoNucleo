package horarios;

import horarios.Model.*;
import java.util.*;

/**
 * Motor de asignación por backtracking con verificación incremental.
 * Heurística: variable más restringida (la sesión con menos candidatas válidas se asigna primero).
 * Desempate blando RB-01: evita días consecutivos para sesiones del mismo grupo.
 */
public final class Motor {
    public record Resultado(boolean factible, List<Asignacion> horario, String causa,
                            long nodos, long retrocesos, long evaluaciones, long millis) {}

    private final List<Regla> reglas;
    private final Parametros p;
    private long nodos, retrocesos, evaluaciones;
    private final long limiteNodos;
    private final boolean dominioPrecalculado;   // optimización evaluada en la sección G
    private final Map<Sesion, List<Asignacion>> dominio = new HashMap<>();

    public Motor(List<Regla> reglas, Parametros p, long limiteNodos) { this(reglas, p, limiteNodos, false); }
    public Motor(List<Regla> reglas, Parametros p, long limiteNodos, boolean dominioPrecalculado) {
        this.reglas = reglas; this.p = p; this.limiteNodos = limiteNodos; this.dominioPrecalculado = dominioPrecalculado;
    }

    /** Expande cursos en sesiones concretas (RF-04 original): grupos según demanda / tope efectivo. */
    public static List<Sesion> expandir(Collection<Curso> cursos, Parametros p) { return expandir(cursos, p, null); }

    /**
     * RF-04 ajustado tras el prototipo: el tamaño de grupo no puede superar la mayor capacidad
     * entre las aulas compatibles con el curso (RD-06 y RD-07), no solo el tope institucional.
     */
    public static List<Sesion> expandir(Collection<Curso> cursos, Parametros p, Collection<Aula> aulas) {
        List<Sesion> out = new ArrayList<>();
        for (Curso c : cursos) {
            int capMax = p.topeEfectivo();
            if (aulas != null) {
                int mejor = 0;
                for (Aula a : aulas) if (a.caracteristicas().containsAll(c.aulaRequerida())) mejor = Math.max(mejor, a.capacidad());
                capMax = Math.min(capMax, Math.max(mejor, 1));
            }
            int grupos = (int) Math.ceil(c.demanda() / (double) capMax);
            int tam = (int) Math.ceil(c.demanda() / (double) grupos);
            for (int g = 1; g <= grupos; g++)
                for (int k = 1; k <= c.sesionesSemana(); k++)
                    out.add(new Sesion(c.codigo() + "-G" + g + "-S" + k, c, g, k, tam));
        }
        return out;
    }

    /** Bloques de 2 h alineados a la hora de inicio (SUP-01) que no cruzan el almuerzo ni la franja (RD-03, RD-04). */
    public static List<Bloque> bloques(Parametros p) {
        List<Bloque> b = new ArrayList<>();
        for (int d = 0; d < 6; d++) {
            int fin = d == 5 ? p.horaFinSab() : p.horaFinLV();
            for (int h = p.horaInicioLV(); h + 2 <= fin; h += 2) {
                boolean cruzaAlmuerzo = h < p.almuerzoInicio() + 1 && h + 2 > p.almuerzoInicio();
                if (!cruzaAlmuerzo) b.add(new Bloque(d, h));
            }
        }
        return b;
    }

    /** Devuelve la explicación del primer rechazo (RF-07) o null si válida. Evalúa todas para reporte completo si se pide. */
    public String rechazo(Asignacion c, List<Asignacion> parcial) { return rechazo(c, parcial, false); }

    private String rechazo(Asignacion c, List<Asignacion> parcial, boolean soloDependientes) {
        for (Regla r : reglas) {
            if (soloDependientes && !r.dependeDelParcial()) continue;
            evaluaciones++;
            Optional<String> e = r.evaluar(c, parcial, p);
            if (e.isPresent()) return r.id() + ": " + e.get();
        }
        return null;
    }

    /** Todas las restricciones que rechazan la candidata (RF-07: reporte completo del conflicto). */
    public List<String> rechazos(Asignacion c, List<Asignacion> parcial) {
        List<String> out = new ArrayList<>();
        for (Regla r : reglas) r.evaluar(c, parcial, p).ifPresent(e -> out.add(r.id() + ": " + e));
        return out;
    }

    public Resultado resolver(List<Sesion> sesiones, List<Aula> aulas, List<Docente> docentes) {
        nodos = retrocesos = evaluaciones = 0;
        long t0 = System.nanoTime();
        List<Asignacion> parcial = new ArrayList<>();
        List<Bloque> bloques = bloques(p);
        dominio.clear();
        if (dominioPrecalculado)   // reglas estáticas se evalúan una sola vez por sesión
            for (Sesion s : sesiones) {
                List<Asignacion> dom = new ArrayList<>();
                for (Bloque b : bloques) for (Aula a : aulas) for (Docente d : docentes) {
                    Asignacion c = new Asignacion(s, b, a, d);
                    boolean ok = true;
                    for (Regla r : reglas) if (!r.dependeDelParcial()) { evaluaciones++; if (r.evaluar(c, List.of(), p).isPresent()) { ok = false; break; } }
                    if (ok) dom.add(c);
                }
                dominio.put(s, dom);
            }
        String[] causa = new String[1];
        boolean ok = bt(new ArrayList<>(sesiones), bloques, aulas, docentes, parcial, causa);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        return new Resultado(ok, parcial, ok ? null : causa[0], nodos, retrocesos, evaluaciones, ms);
    }

    private boolean bt(List<Sesion> pendientes, List<Bloque> bloques, List<Aula> aulas, List<Docente> docentes,
                       List<Asignacion> parcial, String[] causa) {
        if (pendientes.isEmpty()) return true;
        if (nodos > limiteNodos) { causa[0] = "límite de " + limiteNodos + " nodos alcanzado"; return false; }
        // Heurística MRV: calcular candidatas válidas de cada sesión pendiente y elegir la más restringida.
        Sesion elegida = null; List<Asignacion> candidatas = null; String ultimoRechazo = null;
        for (Sesion s : pendientes) {
            List<Asignacion> cs = new ArrayList<>();
            if (dominioPrecalculado) {
                for (Asignacion c : dominio.get(s)) {
                    String r = rechazo(c, parcial, true);
                    if (r == null) cs.add(c); else ultimoRechazo = s.id() + " <- " + r;
                }
            } else {
                for (Bloque b : bloques) for (Aula a : aulas) for (Docente d : docentes) {
                    Asignacion c = new Asignacion(s, b, a, d);
                    String r = rechazo(c, parcial);
                    if (r == null) cs.add(c); else ultimoRechazo = s.id() + " <- " + r;
                }
            }
            if (candidatas == null || cs.size() < candidatas.size()) { elegida = s; candidatas = cs; }
            if (cs.isEmpty()) break;
        }
        if (candidatas.isEmpty()) {
            causa[0] = "sin candidatas para " + elegida.id() + "; último rechazo: " + ultimoRechazo;
            retrocesos++; return false;
        }
        candidatas.sort(Comparator.comparingInt(c -> penalizacionBlanda(c, parcial)));   // RB-01 como desempate
        pendientes.remove(elegida);
        for (Asignacion c : candidatas) {
            nodos++;
            parcial.add(c);
            if (bt(pendientes, bloques, aulas, docentes, parcial, causa)) return true;
            parcial.remove(parcial.size() - 1);
            retrocesos++;
        }
        pendientes.add(elegida);
        return false;
    }

    private int penalizacionBlanda(Asignacion c, List<Asignacion> parcial) {
        int pen = 0;
        for (Asignacion a : parcial)
            if (a.sesion().curso() == c.sesion().curso() && a.sesion().grupo() == c.sesion().grupo()) {
                int dd = Math.abs(a.bloque().dia() - c.bloque().dia());
                if (dd == 0) pen += 10; else if (dd == 1) pen += 3;      // mismo día peor; días consecutivos penalizados
                if (a.bloque().inicio() != c.bloque().inicio()) pen += 1;  // RB-05: misma franja preferida
            }
        return pen;
    }
}

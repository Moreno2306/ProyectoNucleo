package horarios;

import horarios.Model.*;
import java.util.*;

/** Implementaciones concretas de RD-01..RD-10. Una clase por restricción. */
public final class Reglas {
    private Reglas() {}

    static boolean mismoBloque(Bloque a, Bloque b) { return a.dia() == b.dia() && a.inicio() == b.inicio(); }

    public static final class ChoqueDocente implements Regla {               // RD-01
        public String id() { return "RD-01"; }
        public Optional<String> evaluar(Asignacion c, List<Asignacion> parcial, Parametros p) {
            for (Asignacion a : parcial)
                if (a.docente() == c.docente() && mismoBloque(a.bloque(), c.bloque()))
                    return Optional.of("docente " + c.docente().codigo() + " ya dicta " + a.sesion().id() + " en " + c.bloque());
            return Optional.empty();
        }
    }
    public static final class ChoqueAula implements Regla {                  // RD-02
        public String id() { return "RD-02"; }
        public Optional<String> evaluar(Asignacion c, List<Asignacion> parcial, Parametros p) {
            for (Asignacion a : parcial)
                if (a.aula() == c.aula() && mismoBloque(a.bloque(), c.bloque()))
                    return Optional.of("aula " + c.aula().codigo() + " ocupada por " + a.sesion().id() + " en " + c.bloque());
            return Optional.empty();
        }
    }
    public static final class FranjaInstitucional implements Regla {
        public boolean dependeDelParcial() { return false; }         // RD-03
        public String id() { return "RD-03"; }
        public Optional<String> evaluar(Asignacion c, List<Asignacion> parcial, Parametros p) {
            Bloque b = c.bloque();
            int fin = b.dia() == 5 ? p.horaFinSab() : p.horaFinLV();
            if (b.inicio() < p.horaInicioLV() || b.fin() > fin) return Optional.of("bloque " + b + " fuera de franja");
            return Optional.empty();
        }
    }
    public static final class Almuerzo implements Regla {
        public boolean dependeDelParcial() { return false; }                    // RD-04
        public String id() { return "RD-04"; }
        public Optional<String> evaluar(Asignacion c, List<Asignacion> parcial, Parametros p) {
            Bloque b = c.bloque();
            if (b.inicio() < p.almuerzoInicio() + 1 && b.fin() > p.almuerzoInicio()) return Optional.of("bloque " + b + " cruza el almuerzo");
            return Optional.empty();
        }
    }
    public static final class DisponibilidadDocente implements Regla {
        public boolean dependeDelParcial() { return false; }       // RD-05
        public String id() { return "RD-05"; }
        public Optional<String> evaluar(Asignacion c, List<Asignacion> parcial, Parametros p) {
            if (!c.docente().disponible().test(c.bloque())) return Optional.of("docente " + c.docente().codigo() + " no disponible en " + c.bloque());
            return Optional.empty();
        }
    }
    public static final class CapacidadAula implements Regla {
        public boolean dependeDelParcial() { return false; }               // RD-06
        public String id() { return "RD-06"; }
        public Optional<String> evaluar(Asignacion c, List<Asignacion> parcial, Parametros p) {
            if (c.aula().capacidad() < c.sesion().tamano()) return Optional.of("aula " + c.aula().codigo() + " (" + c.aula().capacidad() + ") < grupo de " + c.sesion().tamano());
            return Optional.empty();
        }
    }
    public static final class CaracteristicasAula implements Regla {
        public boolean dependeDelParcial() { return false; }         // RD-07
        public String id() { return "RD-07"; }
        public Optional<String> evaluar(Asignacion c, List<Asignacion> parcial, Parametros p) {
            if (!c.aula().caracteristicas().containsAll(c.sesion().curso().aulaRequerida()))
                return Optional.of("aula " + c.aula().codigo() + " sin " + c.sesion().curso().aulaRequerida());
            return Optional.empty();
        }
    }
    public static final class HabilitacionDocente implements Regla {
        public boolean dependeDelParcial() { return false; }         // RD-08
        public String id() { return "RD-08"; }
        public Optional<String> evaluar(Asignacion c, List<Asignacion> parcial, Parametros p) {
            if (!c.docente().cursosHabilitados().contains(c.sesion().curso().codigo()))
                return Optional.of("docente " + c.docente().codigo() + " no habilitado para " + c.sesion().curso().codigo());
            return Optional.empty();
        }
    }
    public static final class CargaDocente implements Regla {                // RD-09
        public String id() { return "RD-09"; }
        public Optional<String> evaluar(Asignacion c, List<Asignacion> parcial, Parametros p) {
            double cota = c.docente().dedicacion().horas * p.fraccionDocencia();
            int horas = 2;
            for (Asignacion a : parcial) if (a.docente() == c.docente()) horas += 2;
            if (horas > cota) return Optional.of("docente " + c.docente().codigo() + " superaría " + cota + " h de docencia");
            return Optional.empty();
        }
    }
    public static final class MismoDocentePorGrupo implements Regla {        // RD-10 (coherencia: todas las sesiones de un grupo con el mismo docente)
        public String id() { return "RD-10"; }
        public Optional<String> evaluar(Asignacion c, List<Asignacion> parcial, Parametros p) {
            for (Asignacion a : parcial)
                if (a.sesion().curso() == c.sesion().curso() && a.sesion().grupo() == c.sesion().grupo() && a.docente() != c.docente())
                    return Optional.of("grupo " + c.sesion().curso().codigo() + "-G" + c.sesion().grupo() + " ya tiene docente " + a.docente().codigo());
            return Optional.empty();
        }
    }

    public static List<Regla> duras() {
        return List.of(new ChoqueDocente(), new ChoqueAula(), new FranjaInstitucional(), new Almuerzo(),
                new DisponibilidadDocente(), new CapacidadAula(), new CaracteristicasAula(),
                new HabilitacionDocente(), new CargaDocente(), new MismoDocentePorGrupo());
    }
}

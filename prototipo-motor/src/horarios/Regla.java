package horarios;

import horarios.Model.*;
import java.util.List;
import java.util.Optional;

/**
 * Patrón Strategy: cada restricción dura RD-xx es una estrategia independiente.
 * El motor no conoce las reglas concretas (DIP) y agregar una no modifica las demás (OCP).
 */
public interface Regla {
    String id();                       // "RD-01", "RD-02", ...
    /** Devuelve la explicación del rechazo o Optional.empty() si la candidata es válida. */
    Optional<String> evaluar(Asignacion candidata, List<Asignacion> parcial, Parametros p);
    /**
     * true si el veredicto depende de las asignaciones ya hechas (RD-01, RD-02, RD-09, RD-10);
     * false si solo depende de la candidata (RD-03..RD-08). Permite precalcular el dominio de cada sesión.
     */
    default boolean dependeDelParcial() { return true; }
}

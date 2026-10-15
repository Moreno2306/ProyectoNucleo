package horarios;

import horarios.Model.*;
import java.util.*;

/**
 * Prueba de cambio (sección F): nueva regla de negocio "un docente no dicta más de 3 bloques consecutivos el mismo día".
 * Se agrega como clase nueva y se registra; Motor, Reglas existentes y Modelo no se modifican (OCP).
 */
public final class CambioRegla implements Regla {
    public String id() { return "RD-13"; }
    public Optional<String> evaluar(Asignacion c, List<Asignacion> parcial, Parametros p) {
        int seguidos = 1;
        for (int h = c.bloque().inicio() - 2; h >= 7; h -= 2) if (ocupa(c, parcial, c.bloque().dia(), h)) seguidos++; else break;
        for (int h = c.bloque().inicio() + 2; h < 22; h += 2) if (ocupa(c, parcial, c.bloque().dia(), h)) seguidos++; else break;
        return seguidos > 3 ? Optional.of("docente " + c.docente().codigo() + " tendría " + seguidos + " bloques seguidos el día " + c.bloque().dia()) : Optional.empty();
    }
    private static boolean ocupa(Asignacion c, List<Asignacion> parcial, int dia, int h) {
        for (Asignacion a : parcial) if (a.docente() == c.docente() && a.bloque().dia() == dia && a.bloque().inicio() == h) return true;
        return false;
    }

    public static void main(String[] args) {
        Parametros p = new Parametros(40, 0.10, 10, 0.75, 7, 22, 13, 12);
        Datos.Dataset d = Datos.anexoD();
        List<Regla> reglas = new ArrayList<>(Reglas.duras()); reglas.add(new CambioRegla());
        Motor.Resultado r = new Motor(reglas, p, 200_000).resolver(Motor.expandir(d.cursos(), p, d.aulas()), d.aulas(), d.docentes());
        System.out.println("con RD-13: factible=" + r.factible() + " nodos=" + r.nodos() + " ms=" + r.millis());
        // Cambio de parámetro (RF-05): tope 40 -> 35 sin tocar código de reglas
        Parametros p2 = new Parametros(35, 0.10, 10, 0.75, 7, 22, 13, 12);
        Motor.Resultado r2 = new Motor(reglas, p2, 200_000).resolver(Motor.expandir(d.cursos(), p2, d.aulas()), d.aulas(), d.docentes());
        System.out.println("tope=35: sesiones=" + Motor.expandir(d.cursos(), p2, d.aulas()).size() + " factible=" + r2.factible() + " nodos=" + r2.nodos() + " ms=" + r2.millis() + (r2.causa()!=null? " causa="+r2.causa():""));
    }
}

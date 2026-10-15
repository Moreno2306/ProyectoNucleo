package horarios;

import horarios.Model.*;
import java.util.*;

/** Prototipo técnico (Fase 2, sección D y G). Corre el motor sobre Anexo D y escalas ×2, ×4; imprime métricas. */
public final class Main {
    static Motor.Resultado escenario(String nombre, List<Sesion> ses, Datos.Dataset d, Parametros p) {
        Motor.Resultado r = new Motor(Reglas.duras(), p, 200_000).resolver(ses, d.aulas(), d.docentes());
        System.out.printf("%s: sesiones=%d factible=%s nodos=%d retrocesos=%d evaluaciones=%d ms=%d%s%n", nombre, ses.size(), r.factible(),
            r.nodos(), r.retrocesos(), r.evaluaciones(), r.millis(), r.causa() == null ? "" : " causa=" + r.causa());
        return r;
    }

    public static void main(String[] args) {
        Parametros p50 = Parametros.porDefecto();                                    // P-09 = 50 % (valor de trabajo del M1)
        Parametros p75 = new Parametros(40, 0.10, 10, 0.75, 7, 22, 13, 12);           // P-09 = 75 % (cota del Estatuto, EV-02)
        Datos.Dataset base = Datos.anexoD();
        System.out.println("bloques disponibles con SUP-01 + P-03: " + Motor.bloques(p50).size() + " " + Motor.bloques(p50));
        System.out.println("\n=== Escenarios sobre Anexo D (x1) ===");
        escenario("E0 RF-04 original, P-09=50%", Motor.expandir(base.cursos(), p50), base, p50);
        escenario("E1 RF-04 con capacidad de aula, P-09=50%", Motor.expandir(base.cursos(), p50, base.aulas()), base, p50);
        Motor.Resultado rE2 = escenario("E2 RF-04 con capacidad de aula, P-09=75%", Motor.expandir(base.cursos(), p75, base.aulas()), base, p75);

        Parametros p = p50;
        int[] escalas = {1, 2, 3, 4};
        boolean opt = args.length > 0 && args[0].equals("opt");
        System.out.println("\n=== Escalabilidad (configuración E1, motor " + (opt ? "con dominio precalculado" : "ingenuo") + ") ===");
        System.out.println("escala;docentes;aulas;cursos;sesiones;bloques;factible;nodos;retrocesos;evaluaciones;ms;causa");
        for (int k : escalas) {
            Datos.Dataset d = Datos.escalar(base, k);
            List<Sesion> ses = Motor.expandir(d.cursos(), p, d.aulas());
            Motor m = new Motor(Reglas.duras(), p, 200_000, opt);
            Motor.Resultado r = m.resolver(ses, d.aulas(), d.docentes());
            System.out.printf("x%d;%d;%d;%d;%d;%d;%s;%d;%d;%d;%d;%s%n", k, d.docentes().size(), d.aulas().size(), d.cursos().size(),
                ses.size(), Motor.bloques(p).size(), r.factible(), r.nodos(), r.retrocesos(), r.evaluaciones(), r.millis(), r.causa() == null ? "" : r.causa());
            if (k == 1 && r.factible() && !opt) {
                System.out.println("--- horario x1 (primeras 12 asignaciones) ---");
                r.horario().stream().limit(12).forEach(a -> System.out.println(a.sesion().id() + " -> " + a.bloque() + " " + a.aula().codigo() + " " + a.docente().codigo()));
                int consecutivos = 0;
                for (Asignacion a : r.horario()) for (Asignacion b : r.horario())
                    if (a != b && a.sesion().curso() == b.sesion().curso() && a.sesion().grupo() == b.sesion().grupo() && b.bloque().dia() - a.bloque().dia() == 1) consecutivos++;
                System.out.println("pares de sesiones del mismo grupo en días consecutivos (RB-01): " + consecutivos);
                // Prueba de ruptura RF-07: forzar choque de docente y pedir explicación
                Asignacion a0 = r.horario().get(0);
                Asignacion forzada = new Asignacion(r.horario().get(1).sesion(), a0.bloque(), a0.aula(), a0.docente());
                System.out.println("prueba de ruptura (forzar " + forzada.sesion().id() + " al bloque/aula/docente de " + a0.sesion().id() + "): " + m.rechazos(forzada, r.horario()));
                // Prueba de infactibilidad RF-08: aulas reducidas a 1
                Motor m2 = new Motor(Reglas.duras(), p, 50_000);
                Motor.Resultado r2 = m2.resolver(Motor.expandir(d.cursos(), p, d.aulas().subList(3, 4)), d.aulas().subList(3, 4), d.docentes());
                System.out.println("infactibilidad (1 aula): factible=" + r2.factible() + " causa=" + r2.causa() + " ms=" + r2.millis());
            }
        }
    }
}

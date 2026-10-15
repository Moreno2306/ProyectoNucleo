package horarios;

import horarios.Model.*;
import java.util.*;

/** Responde la pregunta del Prototipo 2 (Fase 1): cómo quedan los cursos con computadores en L301 y L302. */
public final class Laboratorios {
    public static void main(String[] args) {
        Parametros p = Parametros.porDefecto();
        Datos.Dataset d = Datos.anexoD();
        List<Sesion> ses = Motor.expandir(d.cursos(), p, d.aulas());
        Motor.Resultado r = new Motor(Reglas.duras(), p, 200_000, true).resolver(ses, d.aulas(), d.docentes());
        System.out.println("factible=" + r.factible() + " nodos=" + r.nodos() + " retrocesos=" + r.retrocesos() + " ms=" + r.millis());
        String[] dias = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"};
        for (String aula : List.of("L301", "L302")) {
            System.out.println("--- " + aula + " ---");
            r.horario().stream().filter(a -> a.aula().codigo().equals(aula))
                .sorted(Comparator.comparingInt((Asignacion a) -> a.bloque().dia()).thenComparingInt(a -> a.bloque().inicio()))
                .forEach(a -> System.out.printf("  %s %02d:00-%02d:00  %-10s %s  (%d est.)%n", dias[a.bloque().dia()], a.bloque().inicio(), a.bloque().fin(), a.sesion().id(), a.docente().codigo(), a.sesion().tamano()));
        }
        long pc = ses.stream().filter(s -> s.curso().aulaRequerida().contains("pc")).count();
        System.out.println("sesiones que requieren computadores: " + pc + " de " + ses.size() + "; capacidad de laboratorio: 2 aulas x 32 bloques = 64 franjas");
        Map<String, Integer> carga = new TreeMap<>();
        for (Asignacion a : r.horario()) carga.merge(a.docente().codigo(), 2, Integer::sum);
        System.out.println("horas semanales asignadas por docente: " + carga);
    }
}

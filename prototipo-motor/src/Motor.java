import java.util.*;
import java.util.function.*;

/** Prototipo técnico PN2 M2 — motor de backtracking con MRV. Un solo archivo para facilitar la lectura. */
public class Motor {

  // ---------- Dominio ----------
  record Parametros(int topeGrupo, double tolerancia, int duracionSesionHoras, double fraccionDocencia,
                    int minGrupo, List<String> dias) {
    int topeEfectivo() { return (int) Math.floor(topeGrupo * (1 + tolerancia)); }
  }
  record Bloque(int id, String dia, int horaInicio) {
    int horaFin() { return horaInicio + 2; }
    public String toString() { return dia + " " + String.format("%02d-%02d", horaInicio, horaFin()); }
  }
  record Aula(String codigo, int capacidad, Set<String> caracteristicas) {}
  record Docente(String codigo, int horasContractuales, Set<String> cursosHabilitados,
                 Predicate<Bloque> disponible, String restriccionTexto) {}
  record Curso(String codigo, String nombre, int semestre, int sesionesSemana, Set<String> aulaRequerida, int demanda) {}
  record Grupo(Curso curso, int numero, int tamano) { String id() { return curso.codigo() + "-G" + numero; } }
  record Sesion(Grupo grupo, int indice) { String id() { return grupo.id() + "-S" + indice; } }
  record Asignacion(Sesion sesion, Bloque bloque, Aula aula, Docente docente) {}

  /** Horario parcial con índices para verificación O(1). */
  static class Horario {
    final Map<Sesion, Asignacion> asignaciones = new LinkedHashMap<>();
    final Set<String> docenteBloque = new HashSet<>();   // "D01|3"
    final Set<String> aulaBloque = new HashSet<>();      // "A101|3"
    final Map<String, Integer> horasDocente = new HashMap<>();
    final Map<String, Docente> docenteGrupo = new HashMap<>();
    final Set<String> grupoDia = new HashSet<>();        // "C01-G1|Lunes"
    void poner(Asignacion a) {
      asignaciones.put(a.sesion(), a);
      docenteBloque.add(a.docente().codigo() + "|" + a.bloque().id());
      aulaBloque.add(a.aula().codigo() + "|" + a.bloque().id());
      horasDocente.merge(a.docente().codigo(), 2, Integer::sum);
      docenteGrupo.putIfAbsent(a.sesion().grupo().id(), a.docente());
      grupoDia.add(a.sesion().grupo().id() + "|" + a.bloque().dia());
    }
    void quitar(Asignacion a) {
      asignaciones.remove(a.sesion());
      docenteBloque.remove(a.docente().codigo() + "|" + a.bloque().id());
      aulaBloque.remove(a.aula().codigo() + "|" + a.bloque().id());
      horasDocente.merge(a.docente().codigo(), -2, Integer::sum);
      boolean otra = asignaciones.keySet().stream().anyMatch(s -> s.grupo().id().equals(a.sesion().grupo().id()));
      if (!otra) docenteGrupo.remove(a.sesion().grupo().id());
      grupoDia.remove(a.sesion().grupo().id() + "|" + a.bloque().dia());
    }
  }

  // ---------- Patrón Strategy: cada restricción dura es una estrategia independiente ----------
  interface RestriccionDura {
    String id();
    /** Devuelve null si la asignación candidata es válida; si no, el motivo del rechazo. */
    String evaluar(Asignacion candidata, Horario h, Parametros p);
  }
  static class ChoqueDocente implements RestriccionDura {
    public String id() { return "RD-01"; }
    public String evaluar(Asignacion c, Horario h, Parametros p) {
      return h.docenteBloque.contains(c.docente().codigo() + "|" + c.bloque().id())
          ? "docente " + c.docente().codigo() + " ya tiene sesión en " + c.bloque() : null; }
  }
  static class ChoqueAula implements RestriccionDura {
    public String id() { return "RD-02"; }
    public String evaluar(Asignacion c, Horario h, Parametros p) {
      return h.aulaBloque.contains(c.aula().codigo() + "|" + c.bloque().id())
          ? "aula " + c.aula().codigo() + " ocupada en " + c.bloque() : null; }
  }
  static class DisponibilidadDocente implements RestriccionDura {
    public String id() { return "RD-05"; }
    public String evaluar(Asignacion c, Horario h, Parametros p) {
      return c.docente().disponible().test(c.bloque()) ? null
          : "docente " + c.docente().codigo() + " no disponible (" + c.docente().restriccionTexto() + ")"; }
  }
  static class CapacidadAula implements RestriccionDura {
    public String id() { return "RD-06"; }
    public String evaluar(Asignacion c, Horario h, Parametros p) {
      return c.aula().capacidad() >= c.sesion().grupo().tamano() ? null
          : "aula " + c.aula().codigo() + " (" + c.aula().capacidad() + ") < grupo " + c.sesion().grupo().tamano(); }
  }
  static class CaracteristicasAula implements RestriccionDura {
    public String id() { return "RD-07"; }
    public String evaluar(Asignacion c, Horario h, Parametros p) {
      return c.aula().caracteristicas().containsAll(c.sesion().grupo().curso().aulaRequerida()) ? null
          : "aula " + c.aula().codigo() + " sin " + c.sesion().grupo().curso().aulaRequerida(); }
  }
  static class HabilitacionDocente implements RestriccionDura {
    public String id() { return "RD-08"; }
    public String evaluar(Asignacion c, Horario h, Parametros p) {
      String curso = c.sesion().grupo().curso().codigo();
      if (!c.docente().cursosHabilitados().contains(curso)) return "docente no habilitado para " + curso;
      Docente ya = h.docenteGrupo.get(c.sesion().grupo().id());
      return (ya == null || ya == c.docente()) ? null : "grupo ya tiene docente " + ya.codigo(); }
  }
  static class CargaDocente implements RestriccionDura {
    public String id() { return "RD-09"; }
    public String evaluar(Asignacion c, Horario h, Parametros p) {
      int max = (int) Math.floor(c.docente().horasContractuales() * p.fraccionDocencia());
      int actual = h.horasDocente.getOrDefault(c.docente().codigo(), 0);
      return actual + 2 <= max ? null : "docente " + c.docente().codigo() + " superaría " + max + " h"; }
  }
  /** Restricción descubierta por el prototipo: dos sesiones del mismo grupo no van el mismo día. */
  static class UnaSesionPorDia implements RestriccionDura {
    public String id() { return "RD-13"; }
    public String evaluar(Asignacion c, Horario h, Parametros p) {
      return h.grupoDia.contains(c.sesion().grupo().id() + "|" + c.bloque().dia())
          ? "grupo ya tiene sesión el " + c.bloque().dia() : null; }
  }
  /** Prueba de cambio (OCP): nueva regla de negocio agregada sin tocar ninguna clase existente. */
  static class MaxSesionesDiaDocente implements RestriccionDura {
    final int max; MaxSesionesDiaDocente(int max) { this.max = max; }
    public String id() { return "RD-14"; }
    public String evaluar(Asignacion c, Horario h, Parametros p) {
      long n = h.asignaciones.values().stream()
          .filter(a -> a.docente() == c.docente() && a.bloque().dia().equals(c.bloque().dia())).count();
      return n < max ? null : "docente " + c.docente().codigo() + " ya tiene " + max + " sesiones el " + c.bloque().dia(); }
  }

  // ---------- Motor ----------
  record Resultado(boolean factible, Horario horario, long nodos, long retrocesos, long ms,
                   String sesionFallida, Map<String, Long> rechazosPorRestriccion) {}

  static class MotorBacktracking {
    final List<RestriccionDura> restricciones; final Parametros p;
    final List<Bloque> bloques; final List<Aula> aulas; final List<Docente> docentes;
    long nodos, retrocesos; Map<String, Long> rechazos = new TreeMap<>(); String fallida; long limiteNodos = 2_000_000; long limiteMs = 60_000; long t0; boolean agotado;
    boolean prefiltrar = false; final Map<String, List<Aula>> aulasPorCurso = new HashMap<>(); final Map<String, List<Docente>> docentesPorCurso = new HashMap<>();
    MotorBacktracking(List<RestriccionDura> r, Parametros p, List<Bloque> b, List<Aula> a, List<Docente> d) {
      restricciones = r; this.p = p; bloques = b; aulas = a; docentes = d; }

    String rechazo(Asignacion c, Horario h) {
      for (RestriccionDura r : restricciones) {
        String m = r.evaluar(c, h, p);
        if (m != null) { rechazos.merge(r.id(), 1L, Long::sum); return r.id() + ": " + m; }
      }
      return null;
    }
    List<Asignacion> candidatos(Sesion s, Horario h) {
      List<Asignacion> out = new ArrayList<>();
      Docente fijo = h.docenteGrupo.get(s.grupo().id());
      Curso cu = s.grupo().curso();
      List<Aula> as = aulas; List<Docente> ds = fijo != null ? List.of(fijo) : docentes;
      if (prefiltrar) { // reducción estática de dominio: solo aulas compatibles y docentes habilitados
        as = aulasPorCurso.computeIfAbsent(cu.codigo(), k -> aulas.stream().filter(a -> a.caracteristicas().containsAll(cu.aulaRequerida()) && a.capacidad() >= s.grupo().tamano()).toList());
        if (fijo == null) ds = docentesPorCurso.computeIfAbsent(cu.codigo(), k -> docentes.stream().filter(d -> d.cursosHabilitados().contains(cu.codigo())).toList());
      }
      for (Bloque b : bloques) for (Aula a : as) for (Docente d : ds) {
        Asignacion c = new Asignacion(s, b, a, d);
        if (rechazo(c, h) == null) out.add(c);
      }
      return out;
    }
    Resultado resolver(List<Sesion> sesiones) {
      t0 = System.nanoTime(); agotado = false; Horario h = new Horario(); nodos = retrocesos = 0; fallida = null; rechazos.clear();
      boolean ok = bt(new ArrayList<>(sesiones), h);
      return new Resultado(ok, h, nodos, retrocesos, (System.nanoTime() - t0) / 1_000_000, agotado ? "LIMITE (" + fallida + ")" : fallida, rechazos);
    }
    boolean bt(List<Sesion> pendientes, Horario h) {
      if (pendientes.isEmpty()) return true;
      if (nodos > limiteNodos || (System.nanoTime() - t0) / 1_000_000 > limiteMs) { agotado = true; return false; }
      // MRV: elegir la sesión con menos candidatos válidos
      Sesion mejor = null; List<Asignacion> mejores = null;
      for (Sesion s : pendientes) {
        List<Asignacion> c = candidatos(s, h);
        if (mejores == null || c.size() < mejores.size()) { mejor = s; mejores = c; }
        if (c.isEmpty()) break;
      }
      if (mejores.isEmpty()) { if (fallida == null) fallida = mejor.id(); retrocesos++; return false; }
      // Desempate blando RB-01: preferir bloques en días no consecutivos al resto del grupo
      final Sesion sel = mejor;
      mejores.sort(Comparator.comparingInt(a -> penalRB01(a, h)));
      pendientes.remove(sel);
      for (Asignacion a : mejores) {
        nodos++; h.poner(a);
        if (bt(pendientes, h)) return true;
        h.quitar(a);
      }
      pendientes.add(sel); retrocesos++;
      return false;
    }
    int penalRB01(Asignacion a, Horario h) {
      int di = p.dias().indexOf(a.bloque().dia()); int pen = 0;
      for (Asignacion o : h.asignaciones.values())
        if (o.sesion().grupo().id().equals(a.sesion().grupo().id()) && Math.abs(p.dias().indexOf(o.bloque().dia()) - di) == 1) pen++;
      return pen;
    }
  }

  // ---------- Generación de grupos (RF-04) ----------
  static List<Grupo> grupos(List<Curso> cursos, List<Aula> aulas, Parametros p, boolean considerarAulas) {
    List<Grupo> out = new ArrayList<>();
    for (Curso c : cursos) {
      int tope = p.topeEfectivo();
      if (considerarAulas) {
        int maxAula = aulas.stream().filter(a -> a.caracteristicas().containsAll(c.aulaRequerida()))
            .mapToInt(Aula::capacidad).max().orElse(0);
        tope = Math.min(tope, maxAula);
      }
      int n = (int) Math.ceil(c.demanda() / (double) tope);
      int tam = (int) Math.ceil(c.demanda() / (double) n);
      for (int i = 1; i <= n; i++) out.add(new Grupo(c, i, tam));
    }
    return out;
  }
  static List<Sesion> sesiones(List<Grupo> gs) {
    List<Sesion> out = new ArrayList<>();
    for (Grupo g : gs) for (int i = 1; i <= g.curso().sesionesSemana(); i++) out.add(new Sesion(g, i));
    return out;
  }
  static List<Bloque> bloques(Parametros p) {
    List<Bloque> out = new ArrayList<>(); int id = 0;
    for (String d : p.dias()) {
      int fin = d.equals("Sabado") ? 13 : 22;
      for (int h = 7; h + 2 <= fin; h += 2) { if (h == 11) { h = 13; if (h + 2 > fin) break; } out.add(new Bloque(id++, d, h)); }
    }
    return out;
  }

  // ---------- Dataset Anexo D ----------
  static Predicate<Bloque> ninguna = b -> true;
  static Predicate<Bloque> despuesDe(int h) { return b -> b.horaInicio() >= h; }
  static Predicate<Bloque> antesDe(int h) { return b -> b.horaFin() <= h; }
  static Predicate<Bloque> noDia(String d) { return b -> !b.dia().equals(d); }
  static Predicate<Bloque> soloDias(String... ds) { Set<String> s = Set.of(ds); return b -> s.contains(b.dia()); }

  static List<Docente> docentesD() { return List.of(
    new Docente("D01", 40, Set.of("C01","C02","C03","C06"), ninguna, "ninguna"),
    new Docente("D02", 40, Set.of("C04","C10","C14","C15"), ninguna, "ninguna"),
    new Docente("D03", 40, Set.of("C05","C11","C12"), antesDe(13), "solo mañana"),
    new Docente("D04", 30, Set.of("C07","C08","C13"), ninguna, "ninguna"),
    new Docente("D05", 30, Set.of("C16","C17"), despuesDe(16), "solo después de 16:00"),
    new Docente("D06", 20, Set.of("C18","C19"), despuesDe(18), "solo después de 18:00"),
    new Docente("D07", 20, Set.of("C09","C20"), noDia("Sabado"), "no dicta sábados"),
    new Docente("D08", 10, Set.of("C01","C06"), soloDias("Lunes","Miercoles"), "solo lunes y miércoles"),
    new Docente("D09", 10, Set.of("C04","C15"), despuesDe(16), "solo después de 16:00"),
    new Docente("D10", 40, Set.of("C02","C03","C14","C20"), noDia("Viernes"), "no dicta viernes")); }
  static List<Aula> aulasD() { return List.of(
    new Aula("A101", 44, Set.of("sillas_moviles")), new Aula("A102", 40, Set.of()),
    new Aula("A201", 30, Set.of("sillas_moviles")), new Aula("L301", 25, Set.of("computadores")),
    new Aula("L302", 35, Set.of("computadores"))); }
  static List<Curso> cursosD() { Set<String> N = Set.of(), PC = Set.of("computadores"), SM = Set.of("sillas_moviles"); return List.of(
    new Curso("C01","Matemáticas Básicas",1,2,N,70), new Curso("C02","Lógica Matemática",1,2,N,38),
    new Curso("C03","Matemáticas Discretas",1,2,N,45), new Curso("C04","Fundamentos de Programación",1,2,PC,60),
    new Curso("C05","Introducción a la Ing. de Sistemas",1,1,SM,42), new Curso("C06","Cálculo Diferencial",2,3,N,36),
    new Curso("C07","Álgebra Lineal",2,2,N,34), new Curso("C08","Física 1",2,2,N,40),
    new Curso("C09","Lógica Difusa",2,1,N,30), new Curso("C10","Programación 1",2,2,PC,48),
    new Curso("C11","Cálculo Integral y Ec. Dif.",3,3,N,32), new Curso("C12","Estadística y Probabilidad",3,2,N,35),
    new Curso("C13","Física 2",3,2,N,33), new Curso("C14","Programación 2",3,2,PC,41),
    new Curso("C15","Estructuras de Datos",3,2,PC,39), new Curso("C16","Compiladores",7,2,PC,24),
    new Curso("C17","Seguridad de la Información",7,2,PC,26), new Curso("C18","Sistemas Inteligentes",8,2,PC,22),
    new Curso("C19","Proyecto de Grado I",8,1,SM,20), new Curso("C20","Proyecto Núcleo 2",6,2,SM,28)); }

  /** Escala el dataset k veces: copia docentes, aulas y cursos con sufijos; cada copia es independiente. */
  static List<Docente> escalarDocentes(List<Docente> ds, int k) { List<Docente> o = new ArrayList<>();
    for (int i = 0; i < k; i++) for (Docente d : ds) { Set<String> cs = new HashSet<>(); for (String c : d.cursosHabilitados()) cs.add(c + "_" + i);
      o.add(new Docente(d.codigo() + "_" + i, d.horasContractuales(), cs, d.disponible(), d.restriccionTexto())); } return o; }
  static List<Aula> escalarAulas(List<Aula> as, int k) { List<Aula> o = new ArrayList<>();
    for (int i = 0; i < k; i++) for (Aula a : as) o.add(new Aula(a.codigo() + "_" + i, a.capacidad(), a.caracteristicas())); return o; }
  static List<Curso> escalarCursos(List<Curso> cs, int k) { List<Curso> o = new ArrayList<>();
    for (int i = 0; i < k; i++) for (Curso c : cs) o.add(new Curso(c.codigo() + "_" + i, c.nombre(), c.semestre(), c.sesionesSemana(), c.aulaRequerida(), c.demanda())); return o; }

  static List<RestriccionDura> base() { return new ArrayList<>(List.of(new ChoqueDocente(), new ChoqueAula(), new DisponibilidadDocente(),
      new CapacidadAula(), new CaracteristicasAula(), new HabilitacionDocente(), new CargaDocente(), new UnaSesionPorDia())); }

  static void imprimir(String titulo, Resultado r, int nSes) {
    System.out.printf("%-48s factible=%-5s sesiones=%-4d nodos=%-8d retrocesos=%-6d t=%d ms%s%n", titulo, r.factible(), nSes,
        r.nodos(), r.retrocesos(), r.ms(), r.factible() ? "" : "  FALLA EN: " + r.sesionFallida());
  }

  public static void main(String[] args) throws Exception {
    Parametros p = new Parametros(40, 0.10, 2, 0.50, 10, List.of("Lunes","Martes","Miercoles","Jueves","Viernes","Sabado"));
    List<Bloque> bl = bloques(p);
    System.out.println("Bloques semanales: " + bl.size());

    // Experimento 1: grupos calculados solo con el tope (RF-04 tal como estaba en M1)
    List<Sesion> s1 = sesiones(grupos(cursosD(), aulasD(), p, false));
    Resultado r1 = new MotorBacktracking(base(), p, bl, aulasD(), docentesD()).resolver(s1);
    imprimir("E1 Anexo D, grupos por tope (RF-04 M1)", r1, s1.size());
    System.out.println("   rechazos por restricción: " + r1.rechazosPorRestriccion());

    // Experimento 2: grupos calculados con tope y capacidad máxima de aula compatible (RF-04 corregido)
    List<Sesion> s2 = sesiones(grupos(cursosD(), aulasD(), p, true));
    Resultado r2 = new MotorBacktracking(base(), p, bl, aulasD(), docentesD()).resolver(s2);
    imprimir("E2 Anexo D, grupos por tope y aula (RF-04 corregido)", r2, s2.size());
    System.out.println("   rechazos por restricción: " + r2.rechazosPorRestriccion());
    if (r2.factible()) {
      try (var w = new java.io.PrintWriter("out/horario_anexoD.csv")) {
        w.println("sesion,grupo_tamano,dia,bloque,aula,docente");
        for (Asignacion a : r2.horario().asignaciones.values())
          w.printf("%s,%d,%s,%02d-%02d,%s,%s%n", a.sesion().id(), a.sesion().grupo().tamano(), a.bloque().dia(), a.bloque().horaInicio(), a.bloque().horaFin(), a.aula().codigo(), a.docente().codigo());
      }
      // Validador independiente: recorre el horario final y re-evalúa todas las restricciones desde cero
      Horario v = new Horario(); int viol = 0; List<RestriccionDura> rs = base();
      for (Asignacion a : r2.horario().asignaciones.values()) { for (RestriccionDura r : rs) if (r.evaluar(a, v, p) != null) viol++; v.poner(a); }
      System.out.println("   validador independiente: violaciones=" + viol);
      // Métrica blanda RB-01
      int consec = 0; List<Asignacion> as = new ArrayList<>(r2.horario().asignaciones.values());
      for (int i = 0; i < as.size(); i++) for (int j = i + 1; j < as.size(); j++)
        if (as.get(i).sesion().grupo().id().equals(as.get(j).sesion().grupo().id())
            && Math.abs(p.dias().indexOf(as.get(i).bloque().dia()) - p.dias().indexOf(as.get(j).bloque().dia())) == 1) consec++;
      System.out.println("   RB-01 pares de sesiones en días consecutivos: " + consec);
    }

    // Experimento 3: prueba de ruptura — forzar un choque de docente y verificar que el motor lo explica
    Horario h = r2.horario(); Asignacion alguna = h.asignaciones.values().iterator().next();
    Sesion otra = s2.stream().filter(s -> !s.equals(alguna.sesion()) && alguna.docente().cursosHabilitados().contains(s.grupo().curso().codigo())).findFirst().orElse(null);
    if (otra != null) {
      Asignacion forzada = new Asignacion(otra, alguna.bloque(), aulasD().get(1), alguna.docente());
      Horario sin = new Horario(); for (Asignacion a : h.asignaciones.values()) if (!a.sesion().equals(otra)) sin.poner(a);
      System.out.println("E3 Prueba de ruptura: mover " + otra.id() + " a " + alguna.bloque() + " con " + alguna.docente().codigo() + " -> "
          + new MotorBacktracking(base(), p, bl, aulasD(), docentesD()).rechazo(forzada, sin));
    }

    // Experimento 4: prueba de cambio OCP — nueva regla RD-14 agregada sin modificar clases existentes
    List<RestriccionDura> conNueva = base(); conNueva.add(new MaxSesionesDiaDocente(3));
    Resultado r4 = new MotorBacktracking(conNueva, p, bl, aulasD(), docentesD()).resolver(s2);
    imprimir("E4 Anexo D + RD-14 (máx 3 sesiones/día docente)", r4, s2.size());

    // Experimento 5: prueba de cambio de parámetro — tope 40 -> 35
    Parametros p35 = new Parametros(35, 0.10, 2, 0.50, 10, p.dias());
    List<Sesion> s5 = sesiones(grupos(cursosD(), aulasD(), p35, true));
    MotorBacktracking m5 = new MotorBacktracking(base(), p35, bl, aulasD(), docentesD()); m5.prefiltrar = true;
    Resultado r5 = m5.resolver(s5);
    imprimir("E5 Anexo D con tope 35 (P-04)", r5, s5.size());

    // Experimento 6: escalabilidad
    System.out.println("E6 Escalabilidad (k copias independientes del Anexo D; aulas y docentes también x k)");
    System.out.println("modo,k,docentes,aulas,cursos,sesiones,factible,nodos,retrocesos,ms,ms_por_nodo");
    for (boolean pre : new boolean[]{false, true}) for (int k : new int[]{1, 2, 3, 4, 6, 8}) {
      if (!pre && k > 3) continue;
      List<Docente> d = escalarDocentes(docentesD(), k); List<Aula> a = escalarAulas(aulasD(), k); List<Curso> c = escalarCursos(cursosD(), k);
      List<Sesion> s = sesiones(grupos(c, a, p, true));
      MotorBacktracking m = new MotorBacktracking(base(), p, bl, a, d); m.prefiltrar = pre; m.limiteMs = 120_000;
      Resultado r = m.resolver(s);
      System.out.printf("%s,%d,%d,%d,%d,%d,%s,%d,%d,%d,%.1f%n", pre ? "prefiltrado" : "ingenuo", k, d.size(), a.size(), c.size(), s.size(), r.factible(), r.nodos(), r.retrocesos(), r.ms(), r.nodos() == 0 ? 0.0 : r.ms() / (double) r.nodos());
    }
    // Experimento 7: escalar solo cursos (recursos fijos) hasta la infactibilidad
    System.out.println("E7 Más cursos con recursos fijos (x k cursos, 10 docentes y 5 aulas)");
    for (int k : new int[]{1, 2, 3}) {
      List<Curso> c = escalarCursos(cursosD(), k); List<Docente> d = docentesD().stream().map(x -> { Set<String> cs = new HashSet<>();
        for (int i = 0; i < k; i++) for (String s : x.cursosHabilitados()) cs.add(s + "_" + i); return new Docente(x.codigo(), x.horasContractuales(), cs, x.disponible(), x.restriccionTexto()); }).toList();
      List<Sesion> s = sesiones(grupos(c, aulasD(), p, true));
      MotorBacktracking m = new MotorBacktracking(base(), p, bl, aulasD(), d); m.prefiltrar = true; m.limiteMs = 60_000;
      Resultado r = m.resolver(s);
      imprimir("   cursos x" + k, r, s.size());
      if (!r.factible()) System.out.println("   rechazos por restricción: " + r.rechazosPorRestriccion());
    }
  }
}

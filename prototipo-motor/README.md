# Prototipo técnico — motor de restricciones (PN2, Módulo 2, Fase 2)

Prototipo desechable en Java 21, sin dependencias. Responde la pregunta del prototipo técnico de la Fase 1:
¿un backtracking con reglas independientes genera un horario válido para el Anexo D en < 60 s, explica cada rechazo y reporta infactibilidad?

## Compilar y correr

    javac -d out src/horarios/*.java
    java -cp out horarios.Main          # motor ingenuo: escenarios E0/E1/E2 + escalabilidad x1..x4 (~3 min)
    java -cp out horarios.Main opt      # motor con dominio precalculado (~10 s)
    java -cp out horarios.CambioRegla   # prueba de cambio: regla RD-13 nueva y tope 40 -> 35
    java -cp out horarios.Laboratorios  # pregunta del Prototipo 2: ocupación de L301/L302 y carga por docente

## Archivos

- `Model.java`      registros del dominio (Bloque, Aula, Docente, Curso, Sesion, Asignacion, Parametros)
- `Regla.java`      interfaz Strategy de las restricciones
- `Reglas.java`     RD-01 a RD-10, una clase por restricción
- `Motor.java`      backtracking + heurística de variable más restringida + dominio precalculado
- `Datos.java`      Anexo D del M1 y generador de escala xk
- `Main.java`       corridas y métricas
- `Laboratorios.java` ocupación de laboratorios y horas por docente (Prototipo 2 de la Fase 1)
- `CambioRegla.java` RD-13 (máx. 3 bloques seguidos por docente), agregada sin tocar el motor
- `resultados/`     salida de consola de las tres corridas usadas en el dossier
- `img/`            diagramas (.dot y .png), wireframe y gráfica de escalabilidad

## Cómo subirlo al repositorio sin que parezca un volcado

Un commit por archivo o por pareja de archivos, hecho por la persona que lo va a defender, con mensaje que diga qué decide ese archivo
(por ejemplo: "Regla: interfaz Strategy para RD-xx con dependeDelParcial"). El historial se audita y la verificación individual vale 25 puntos.

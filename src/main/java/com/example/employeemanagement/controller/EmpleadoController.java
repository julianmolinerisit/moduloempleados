package com.example.employeemanagement.controller;

import com.example.employeemanagement.model.Empleado;
import com.example.employeemanagement.model.Empleado.Horario;
import com.example.employeemanagement.model.RegistroEntradaSalida;
import com.example.employeemanagement.service.EmpleadoService;
import com.example.employeemanagement.service.FaceRecognitionService;

import ch.qos.logback.core.util.Duration;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/empleados")
public class EmpleadoController {

	private static final Logger logger = LoggerFactory.getLogger(EmpleadoController.class);

	@Autowired
	private EmpleadoService empleadoService;

	@Autowired
	private FaceRecognitionService faceRecognitionService;

	private static final String UPLOAD_DIR = "src/main/resources/static/imagenes_empleados/";

	@GetMapping
	public String getAllEmpleados(Model model) {
		model.addAttribute("empleados", empleadoService.getAllEmpleados());
		return "empleados";
	}

	@GetMapping("/nuevo")
	public String showCreateForm(Model model) {
		model.addAttribute("empleado", new Empleado());
		return "crear_empleado";
	}

	private String guardarImagenDesdeDataUri(String dataUri) {
		String base64Image = dataUri.split(",")[1];
		byte[] imageBytes = Base64.getDecoder().decode(base64Image);

		String imageName = UUID.randomUUID().toString() + ".jpg";
		String filePath = UPLOAD_DIR + imageName;

		try (FileOutputStream fos = new FileOutputStream(filePath)) {
			fos.write(imageBytes);
		} catch (IOException e) {
			logger.error("Error al guardar la imagen: {}", e.getMessage());
			e.printStackTrace();
		}

		return "/imagenes_empleados/" + imageName; // Retornar la ruta relativa de la imagen
	}

	@PostMapping
	public String createEmpleado(@ModelAttribute Empleado empleado, @RequestParam("image") String imageData) {
		if (imageData != null && !imageData.isEmpty()) {
			String rutaImagen = guardarImagenDesdeDataUri(imageData);
			empleado.setImagen(rutaImagen);
		}
		empleadoService.saveEmpleado(empleado);
		return "redirect:/empleados";
	}

	@GetMapping("/editar/{id}")
	public String showEditForm(@PathVariable Long id, Model model) {
		Optional<Empleado> empleado = empleadoService.getEmpleadoById(id);
		if (empleado.isPresent()) {
			model.addAttribute("empleado", empleado.get());
			return "editar_empleado";
		}
		return "redirect:/empleados";
	}

	@PostMapping("/{id}")
	public String updateEmpleado(@PathVariable Long id, @ModelAttribute Empleado empleado) {
		empleado.setId(id);
		empleadoService.saveEmpleado(empleado);
		return "redirect:/empleados";
	}

	@GetMapping("/borrar/{id}")
	public String deleteEmpleado(@PathVariable Long id) {
		empleadoService.deleteEmpleado(id);
		return "redirect:/empleados";
	}

	@GetMapping("/entrada-salida")
	public String mostrarFormularioEntradaSalida(Model model) {
		model.addAttribute("empleado", new Empleado());
		return "entrada_salida";
	}

	@PostMapping("/entrada-salida")
	public String registrarEntradaSalida(@RequestParam String dni, @RequestParam("image") String imageData,
			Model model) {
		logger.info("Iniciando el proceso de registro de entrada/salida para DNI: {}", dni);
		Optional<Empleado> empleadoOpt = empleadoService.getEmpleadoByDni(dni);

		if (empleadoOpt.isPresent()) {
			Empleado emp = empleadoOpt.get();
			logger.info("Empleado encontrado: {}", emp);

			try {
				// Registrar entrada o salida usando el servicio
				LocalDateTime ahora = LocalDateTime.now();
				RegistroEntradaSalida nuevoRegistro = new RegistroEntradaSalida();
				nuevoRegistro.setTimestamp(ahora);

				// Determinar si es entrada o salida
				List<RegistroEntradaSalida> registros = emp.getRegistros();
				boolean esEntrada = registros.isEmpty() || !registros.get(registros.size() - 1).isEsEntrada();
				nuevoRegistro.setEsEntrada(esEntrada);

				// Obtener el horario asignado para hoy
				Optional<Empleado.Horario> horarioAsignado = obtenerHorarioAsignadoParaHoy(emp);
				String mensajeLlegada = "";
				long minutosTarde = 0;

				if (horarioAsignado.isPresent()) {
					Empleado.Horario horario = horarioAsignado.get();
					LocalTime horaIngreso = horario.getIngreso();
					LocalTime horaSalida = horario.getSalida();

					// Verificar si es entrada o salida y comparar con horarios
					if (esEntrada) {
						if (ahora.toLocalTime().isAfter(horaIngreso)) {
							minutosTarde = calcularMinutosDeRetraso(horaIngreso, ahora.toLocalTime());
							mensajeLlegada = "Llegó tarde. Tiempo de retraso: " + minutosTarde + " minutos.";
						} else {
							mensajeLlegada = "Llegó a tiempo.";
						}
					} else {
						if (ahora.toLocalTime().isAfter(horaSalida)) {
							minutosTarde = calcularMinutosDeRetraso(horaSalida, ahora.toLocalTime());
							mensajeLlegada = "Salida tarde. Tiempo de retraso: " + minutosTarde + " minutos.";
						} else {
							mensajeLlegada = "Salió a tiempo.";
						}
					}
					nuevoRegistro.setMinutosRetraso(minutosTarde);
				} else {
					mensajeLlegada = "Horario no asignado para hoy.";
				}

				// Si se proporciona una imagen, guardarla y verificar con Face++
				if (imageData != null && !imageData.isEmpty()) {
					String rutaImagen = guardarImagenDesdeDataUri(imageData);
					nuevoRegistro.setImagen(rutaImagen);

					// Convertir la imagen a Base64 para comparación
					String base64Image = imageData.split(",")[1];

					// Obtener la imagen almacenada del empleado
					String storedImagePath = UPLOAD_DIR + emp.getImagen().split("/")[2];
					String storedBase64Image = Base64.getEncoder()
							.encodeToString(Files.readAllBytes(Paths.get(storedImagePath)));

					// Comparar imágenes con Face++
					String compareResult = faceRecognitionService.compareFaces(base64Image, storedBase64Image);
					logger.info("Resultado de la comparación de reconocimiento facial: {}", compareResult);

					// Analizar el resultado de la comparación (parsear el JSON)
					JSONObject json = new JSONObject(compareResult);

					if (json.has("confidence")) {
						double confidence = json.getDouble("confidence");

						if (confidence >= 75.0) { // Umbral de confianza
							logger.info("Reconocimiento facial exitoso. Confianza: {}", confidence);
							// Guardar el nuevo registro
							emp.agregarRegistro(nuevoRegistro);
							empleadoService.saveEmpleado(emp);
							model.addAttribute("mensaje", mensajeLlegada + " Registro guardado con éxito.");
						} else {
							logger.warn("Reconocimiento facial fallido. Confianza: {}", confidence);
							model.addAttribute("mensaje", "Reconocimiento facial fallido.");
						}
					} else {
						logger.error("Error en la respuesta de reconocimiento facial. Detalles: {}", json.toString());
						model.addAttribute("mensaje", "Error en la respuesta de reconocimiento facial.");
					}
				} else {
					// Guardar el nuevo registro sin imagen
					emp.agregarRegistro(nuevoRegistro);
					empleadoService.saveEmpleado(emp);
					model.addAttribute("mensaje", mensajeLlegada + " Registro guardado con éxito.");
				}

				// Permanecer en la misma página y mostrar mensaje
				return "entrada_salida";
			} catch (Exception e) {
				logger.error("Error al registrar entrada/salida: ", e);
				model.addAttribute("mensaje", "Error al registrar entrada/salida.");
				return "entrada_salida";
			}
		} else {
			model.addAttribute("mensaje", "Empleado no encontrado.");
			return "entrada_salida";
		}
	}

	private Optional<Empleado.Horario> obtenerHorarioAsignadoParaHoy(Empleado emp) {
		// Obtén el día de la semana actual en formato String (ej. "Lunes", "Martes",
		// etc.)
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE", new Locale("es"));
		String diaSemanaHoy = LocalDate.now().format(formatter).toLowerCase();

		// Imprime el día de la semana actual para depuración
		logger.info("Día de la semana actual: {}", diaSemanaHoy);

		// Verifica si el empleado tiene asignado el día actual
		boolean diaAsignado = emp.getDiasAsignados().stream().map(String::toLowerCase) // Convierte los días asignados a
																						// minúsculas para comparación
				.anyMatch(dia -> dia.equals(diaSemanaHoy));

		// Imprime si el día está asignado o no
		logger.info("Día asignado para hoy: {}", diaAsignado);

		// Si el día está asignado, obten el horario asignado para el empleado
		if (diaAsignado) {
			// Asume que todos los días asignados tienen el mismo horario
			return emp.getHorariosAsignados().stream().findFirst(); // Obtén el primer horario disponible
		}

		return Optional.empty();
	}

	// Método para calcular minutos de retraso
	private long calcularMinutosDeRetraso(LocalTime horaAsignada, LocalTime horaActual) {
		int minutosAsignados = horaAsignada.getHour() * 60 + horaAsignada.getMinute();
		int minutosActuales = horaActual.getHour() * 60 + horaActual.getMinute();
		return minutosActuales - minutosAsignados;
	}

	@GetMapping("/index")
	public String mostrarIndex(@RequestParam("id") Long id,
			@RequestParam(value = "filtro", required = false) String filtro,
			@RequestParam(value = "semanaInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semanaInicio,
			@RequestParam(value = "semanaFin", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semanaFin,
			@RequestParam(value = "mes", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) YearMonth mes,
			@RequestParam(value = "anio", required = false) Integer anio,
			@RequestParam(value = "dia", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
			Model model) {
		Optional<Empleado> empleadoOpt = empleadoService.getEmpleadoById(id);
		if (empleadoOpt.isPresent()) {
			Empleado empleado = empleadoOpt.get();
			model.addAttribute("empleado", empleado);

			model.addAttribute("horariosAsignados",
					empleado.getHorariosAsignados().stream()
							.map(h -> "Ingreso: " + h.getIngreso() + ", Salida: " + h.getSalida())
							.collect(Collectors.toList()));
			model.addAttribute("diasAsignados", empleado.getDiasAsignados());

			// Generar calendario de asistencia con filtro
			Map<String, List<Map.Entry<String, String>>> calendario = generarCalendarioAsistencia(empleado, filtro,
					semanaInicio, semanaFin, mes, anio, dia);
			model.addAttribute("calendario", calendario);

			return "index"; // Asegúrate de que index.html esté en src/main/resources/templates/
		} else {
			return "redirect:/empleados"; // Redirigir si el empleado no se encuentra
		}
	}

	// Método para generar el calendario detallado
	private Map<String, List<Map.Entry<String, String>>> generarCalendarioAsistencia(Empleado empleado, String filtro,
			LocalDate semanaInicio, LocalDate semanaFin, YearMonth mes, Integer anio, LocalDate dia) {

		Map<String, List<Map.Entry<String, String>>> calendario = new LinkedHashMap<>();
		LocalDate fechaInicio;
		LocalDate fechaFin;

		// Definir el rango de fechas según el filtro
		if ("mes".equals(filtro) && mes != null) {
			LocalDate primerDiaDelMes = mes.atDay(1);
			LocalDate ultimoDiaDelMes = mes.atEndOfMonth();
			fechaInicio = primerDiaDelMes.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
			fechaFin = ultimoDiaDelMes.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
		} else if ("año".equals(filtro) && anio != null) {
			fechaInicio = LocalDate.of(anio, 1, 1);
			fechaFin = LocalDate.of(anio, 12, 31);
		} else if ("semana".equals(filtro) && semanaInicio != null && semanaFin != null) {
			fechaInicio = semanaInicio.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
			fechaFin = semanaFin.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
		} else if ("día".equals(filtro) && dia != null) {
			fechaInicio = dia;
			fechaFin = dia;
		} else {
			fechaInicio = LocalDate.now().withDayOfMonth(1);
			fechaFin = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
		}

		DateTimeFormatter horaFormatter = DateTimeFormatter.ofPattern("HH:mm");

		// Generar el calendario día a día
		for (LocalDate date = fechaInicio; !date.isAfter(fechaFin); date = date.plusDays(1)) {
			List<Map.Entry<String, String>> registrosDelDia = new ArrayList<>();

			// Buscar registros para la fecha actual
			boolean tieneRegistro = false;
			for (RegistroEntradaSalida registro : empleado.getRegistros()) {
				if (registro.getTimestamp().toLocalDate().equals(date)) {
					tieneRegistro = true;
					String estado = determinarEstadoAsistencia(registro, empleado);
					String hora = registro.getTimestamp().toLocalTime().format(horaFormatter);
					String tipo = registro.isEsEntrada() ? "Entrada" : "Salida";
					String detalle = estado + " - Retraso: " + registro.getMinutosRetraso() + " min";
					registrosDelDia.add(new AbstractMap.SimpleEntry<>("Hora: " + hora, tipo + " - " + detalle));
				}
			}

			if (!tieneRegistro) {
				registrosDelDia.add(new AbstractMap.SimpleEntry<>("Hora: ", "No se registró entrada/salida."));
			}

			calendario.put(date.toString(), registrosDelDia);
		}

		return calendario;
	}

	// Método para generar el calendario simplificado
	private Map<String, List<Map.Entry<String, String>>> generarCalendarioAsistenciaSimplificado(Empleado empleado,
			String filtro, LocalDate semanaInicio, LocalDate semanaFin, YearMonth mes, Integer anio, LocalDate dia) {

		Map<String, List<Map.Entry<String, String>>> calendario = new LinkedHashMap<>();
		LocalDate fechaInicio;
		LocalDate fechaFin;

		// Definir el rango de fechas según el filtro
		if ("mes".equals(filtro) && mes != null) {
			LocalDate primerDiaDelMes = mes.atDay(1);
			LocalDate ultimoDiaDelMes = mes.atEndOfMonth();
			fechaInicio = primerDiaDelMes.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
			fechaFin = ultimoDiaDelMes.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
		} else if ("año".equals(filtro) && anio != null) {
			fechaInicio = LocalDate.of(anio, 1, 1);
			fechaFin = LocalDate.of(anio, 12, 31);
		} else if ("semana".equals(filtro) && semanaInicio != null && semanaFin != null) {
			fechaInicio = semanaInicio.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
			fechaFin = semanaFin.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
		} else if ("día".equals(filtro) && dia != null) {
			fechaInicio = dia;
			fechaFin = dia;
		} else {
			fechaInicio = LocalDate.now().withDayOfMonth(1);
			fechaFin = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
		}

		DateTimeFormatter horaFormatter = DateTimeFormatter.ofPattern("HH:mm");

		// Generar el calendario día a día
		for (LocalDate date = fechaInicio; !date.isAfter(fechaFin); date = date.plusDays(1)) {
			List<Map.Entry<String, String>> registrosDelDia = new ArrayList<>();

			// Buscar registros para la fecha actual
			boolean tieneRegistro = false;
			for (RegistroEntradaSalida registro : empleado.getRegistros()) {
				if (registro.getTimestamp().toLocalDate().equals(date)) {
					tieneRegistro = true;
					String hora = registro.getTimestamp().toLocalTime().format(horaFormatter);
					String tipo = registro.isEsEntrada() ? "Entrada" : "Salida";
					registrosDelDia.add(new AbstractMap.SimpleEntry<>(hora, tipo));
				}
			}

			if (!tieneRegistro) {
				registrosDelDia.add(new AbstractMap.SimpleEntry<>("No hay registros", ""));
			}

			calendario.put(date.toString(), registrosDelDia);
		}

		return calendario;
	}

	private Optional<Empleado.Horario> obtenerHorarioAsignadoParaDia(Empleado emp, LocalDate date) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE", new Locale("es"));
		String diaSemana = date.format(formatter).toLowerCase();

		boolean diaAsignado = emp.getDiasAsignados().stream().map(String::toLowerCase)
				.anyMatch(dia -> dia.equals(diaSemana));

		if (diaAsignado) {
			return emp.getHorariosAsignados().stream().findFirst();
		}

		return Optional.empty();
	}

	@GetMapping("/calendario-detallado")
	public String mostrarCalendarioDetallado(Model model, Empleado empleado, String filtro, LocalDate semanaInicio,
	        LocalDate semanaFin, YearMonth mes, Integer anio, LocalDate dia) {
	    Map<String, List<Map.Entry<String, String>>> calendario = generarCalendarioAsistencia(empleado, filtro,
	            semanaInicio, semanaFin, mes, anio, dia);
	    model.addAttribute("calendario", calendario);
	    return "calendario-detallado";
	}

	@GetMapping("/calendario-simplificado")
	public String mostrarCalendarioSimplificado(Model model, Empleado empleado, String filtro, LocalDate semanaInicio,
	        LocalDate semanaFin, YearMonth mes, Integer anio, LocalDate dia) {
	    Map<String, List<Map.Entry<String, String>>> calendarioSimplificado = generarCalendarioAsistenciaSimplificado(
	            empleado, filtro, semanaInicio, semanaFin, mes, anio, dia);
	    model.addAttribute("calendarioSimplificado", calendarioSimplificado);
	    return "calendario-simplificado";
	}

	// Ajustar el método obtenerHorarioAsignadoParaHoy para más detalles de
	// depuración

	// Ajustar el método determinarEstadoAsistencia para más detalles de depuración
	private String determinarEstadoAsistencia(RegistroEntradaSalida registro, Empleado empleado) {
		Optional<Empleado.Horario> horarioAsignado = obtenerHorarioAsignadoParaHoy(empleado);

		if (horarioAsignado.isPresent()) {
			Empleado.Horario horario = horarioAsignado.get();
			LocalTime horaIngreso = horario.getIngreso();
			LocalTime horaSalida = horario.getSalida();

			logger.info("Horario asignado para hoy - Ingreso: {}, Salida: {}", horaIngreso, horaSalida);
			logger.info("Registro - Timestamp: {}, Es Entrada: {}", registro.getTimestamp(), registro.isEsEntrada());

			if (registro.isEsEntrada()) {
				if (registro.getTimestamp().toLocalTime().isAfter(horaIngreso)) {
					return "Tarde";
				} else {
					return "En hora";
				}
			} else {
				if (registro.getTimestamp().toLocalTime().isAfter(horaSalida)) {
					return "Tarde";
				} else {
					return "En hora";
				}
			}
		} else {
			logger.warn("No hay horario asignado para hoy");
			return "unassigned";
		}
	}

	@GetMapping("/asistencia/{id}")
	public String mostrarCalendarioAsistencia(@PathVariable Long id,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate semanaInicio,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate semanaFin,
			@RequestParam(required = false) Integer mes, @RequestParam(required = false) Integer anio,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") List<LocalDate> dias, Model model) {
		Optional<Empleado> empleadoOpt = empleadoService.getEmpleadoById(id);

		if (empleadoOpt.isPresent()) {
			Empleado empleado = empleadoOpt.get();
			List<LocalDate> fechas = new ArrayList<>();

			if (semanaInicio != null && semanaFin != null) {
				fechas = semanaInicio.datesUntil(semanaFin.plusDays(1)).collect(Collectors.toList());
			} else if (mes != null && anio != null) {
				YearMonth yearMonth = YearMonth.of(anio, mes);
				fechas = yearMonth.atDay(1).datesUntil(yearMonth.atEndOfMonth().plusDays(1))
						.collect(Collectors.toList());
			} else if (dias != null && !dias.isEmpty()) {
				fechas.addAll(dias);
			} else {
				LocalDate hoy = LocalDate.now();
				LocalDate primerDiaSemana = hoy.with(DayOfWeek.MONDAY);
				LocalDate ultimoDiaSemana = hoy.with(DayOfWeek.SUNDAY);
				fechas = primerDiaSemana.datesUntil(ultimoDiaSemana.plusDays(1)).collect(Collectors.toList());
			}

			Map<LocalDate, String> estadoAsistencia = new HashMap<>();

			for (LocalDate fecha : fechas) {
				estadoAsistencia.put(fecha, "No asignado");
			}

			for (RegistroEntradaSalida registro : empleado.getRegistros()) {
				LocalDate fechaRegistro = registro.getTimestamp().toLocalDate();
				if (estadoAsistencia.containsKey(fechaRegistro)) {
					if (registro.isEsEntrada()) {
						estadoAsistencia.put(fechaRegistro, registro.getMinutosRetraso() > 0 ? "Tarde" : "A tiempo");
					} else {
						estadoAsistencia.put(fechaRegistro, "Ausente");
					}
				}
			}

			model.addAttribute("empleado", empleado);
			model.addAttribute("estadoAsistencia", estadoAsistencia);
			model.addAttribute("fechas", fechas);
			return "calendario_asistencia";
		} else {
			return "redirect:/empleados";
		}

	}
}
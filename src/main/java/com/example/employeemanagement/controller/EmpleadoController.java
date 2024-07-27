package com.example.employeemanagement.controller;

import com.example.employeemanagement.model.Empleado;
import com.example.employeemanagement.model.RegistroEntradaSalida;
import com.example.employeemanagement.service.EmpleadoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/empleados")
public class EmpleadoController {

	@Autowired
	private EmpleadoService empleadoService;

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

	private static final String UPLOAD_DIR = "src/main/resources/static/imagenes_empleados/";

	private String guardarImagenDesdeDataUri(String dataUri) {
		String base64Image = dataUri.split(",")[1];
		byte[] imageBytes = Base64.getDecoder().decode(base64Image);

		String imageName = UUID.randomUUID().toString() + ".jpg";
		String filePath = UPLOAD_DIR + imageName;

		try (FileOutputStream fos = new FileOutputStream(filePath)) {
			fos.write(imageBytes);
		} catch (IOException e) {
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
		return "entrada_salida"; // Asegúrate de que entrada_salida.html esté en
									// src/main/resources/templates/empleados/
	}

	
	
	private static final String UPLOAD_DIR_IMAGENES_ENTRADAS_SALIDAS = "src/main/resources/static/imagenes_entradas_salidas/";

	private String guardarImagenDesdeDataUri(String dataUri, String uploadDir) {
	    String base64Image = dataUri.split(",")[1];
	    byte[] imageBytes = Base64.getDecoder().decode(base64Image);

	    String imageName = UUID.randomUUID().toString() + ".jpg";
	    String filePath = uploadDir + imageName;

	    try (FileOutputStream fos = new FileOutputStream(filePath)) {
	        fos.write(imageBytes);
	    } catch (IOException e) {
	        e.printStackTrace();
	    }

	    return "/imagenes_entradas_salidas/" + imageName; // Retornar la ruta relativa de la imagen
	}
	
	@PostMapping("/entrada-salida")
	public String registrarEntradaSalida(@RequestParam String dni, 
	                                      @RequestParam("image") String imageData,
	                                      Model model) {
	    Optional<Empleado> empleadoOpt = empleadoService.getEmpleadoByDni(dni);
	    
	    if (empleadoOpt.isPresent()) {
	        Empleado emp = empleadoOpt.get();
	        
	        try {
	            // Registrar entrada o salida usando el servicio
	            // Crear un nuevo registro de entrada o salida
	            LocalDateTime ahora = LocalDateTime.now();
	            RegistroEntradaSalida nuevoRegistro = new RegistroEntradaSalida();
	            nuevoRegistro.setTimestamp(ahora);
	            
	            // Determinar si es entrada o salida
	            List<RegistroEntradaSalida> registros = emp.getRegistros();
	            if (registros.isEmpty()) {
	                nuevoRegistro.setEsEntrada(true);
	            } else {
	                RegistroEntradaSalida ultimoRegistro = registros.get(registros.size() - 1);
	                if (ultimoRegistro.isEsEntrada() && ultimoRegistro.getTimestamp().isBefore(ahora)) {
	                    nuevoRegistro.setEsEntrada(false);
	                } else {
	                    nuevoRegistro.setEsEntrada(true);
	                }
	            }

	            // Si se proporciona una imagen, guardarla
	            if (imageData != null && !imageData.isEmpty()) {
	                String rutaImagen = guardarImagenDesdeDataUri(imageData, UPLOAD_DIR_IMAGENES_ENTRADAS_SALIDAS);
	                nuevoRegistro.setImagen(rutaImagen); // Asignar la ruta de la imagen al registro
	            }
	            
	            // Agregar el nuevo registro a la lista de registros del empleado
	            emp.agregarRegistro(nuevoRegistro);
	            
	            // Guardar el empleado con el nuevo registro
	            empleadoService.saveEmpleado(emp);
	            
	            // Preparar datos para la vista
	            model.addAttribute("empleado", emp);
	            model.addAttribute("horariosAsignados",
	                    emp.getHorariosAsignados().stream()
	                            .map(h -> "Ingreso: " + h.getIngreso() + ", Salida: " + h.getSalida())
	                            .collect(Collectors.toList()));
	            model.addAttribute("diasAsignados", emp.getDiasAsignados());

	            // Lógica para generar el calendario de asistencia
	            Map<String, List<Map.Entry<String, String>>> calendario = generarCalendarioAsistencia(emp);
	            model.addAttribute("calendario", calendario);

	            // Redirigir a la página de información del empleado
	            return "redirect:/empleados/index?id=" + emp.getId(); // Pasar ID del empleado
	        } catch (Exception e) {
	            model.addAttribute("mensaje", e.getMessage());
	            return "entrada_salida"; // Mostrar mensaje de error en la misma página
	        }
	    } else {
	        model.addAttribute("mensaje", "Empleado no encontrado.");
	        return "entrada_salida"; // Mostrar mensaje de error en la misma página
	    }
	}


	@GetMapping("/index")
	public String mostrarIndex(@RequestParam("id") Long id, Model model) {
		Optional<Empleado> empleadoOpt = empleadoService.getEmpleadoById(id);
		if (empleadoOpt.isPresent()) {
			Empleado empleado = empleadoOpt.get();
			model.addAttribute("empleado", empleado);
			model.addAttribute("horariosAsignados",
					empleado.getHorariosAsignados().stream()
							.map(h -> "Ingreso: " + h.getIngreso() + ", Salida: " + h.getSalida())
							.collect(Collectors.toList()));
			model.addAttribute("diasAsignados", empleado.getDiasAsignados());

			// Generar calendario de asistencia
			Map<String, List<Map.Entry<String, String>>> calendario = generarCalendarioAsistencia(empleado);
			model.addAttribute("calendario", calendario);

			return "index"; // Asegúrate de que index.html esté en src/main/resources/templates/
		} else {
			return "redirect:/empleados"; // Redirigir si el empleado no se encuentra
		}
	}

	private Map<String, List<Map.Entry<String, String>>> generarCalendarioAsistencia(Empleado empleado) {
		Map<String, List<Map.Entry<String, String>>> calendario = new HashMap<>();
		LocalDateTime hoy = LocalDateTime.now();

		// Lógica para generar el calendario basado en los registros de entrada/salida
		for (RegistroEntradaSalida registro : empleado.getRegistros()) {
			String fecha = registro.getTimestamp().toLocalDate().toString();
			String estado = registro.isEsEntrada() ? "Entrada" : "Salida";

			calendario.computeIfAbsent(fecha, k -> new ArrayList<>())
					.add(new AbstractMap.SimpleEntry<>(registro.getTimestamp().toLocalTime().toString(), estado));
		}

		return calendario;
	}
}
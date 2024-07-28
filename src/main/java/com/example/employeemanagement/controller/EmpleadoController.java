package com.example.employeemanagement.controller;

import com.example.employeemanagement.model.Empleado;
import com.example.employeemanagement.model.RegistroEntradaSalida;
import com.example.employeemanagement.service.EmpleadoService;
import com.example.employeemanagement.service.FaceRecognitionService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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
    public String registrarEntradaSalida(@RequestParam String dni,
                                         @RequestParam("image") String imageData,
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
                if (registros.isEmpty()) {
                    nuevoRegistro.setEsEntrada(true);
                } else {
                    RegistroEntradaSalida ultimoRegistro = registros.get(registros.size() - 1);
                    nuevoRegistro.setEsEntrada(!ultimoRegistro.isEsEntrada() && ultimoRegistro.getTimestamp().isBefore(ahora));
                }

                // Si se proporciona una imagen, guardarla y verificar con Face++
                if (imageData != null && !imageData.isEmpty()) {
                    String rutaImagen = guardarImagenDesdeDataUri(imageData);
                    nuevoRegistro.setImagen(rutaImagen);

                    // Convertir la imagen a Base64 para comparación
                    String base64Image = imageData.split(",")[1];

                    // Obtener la imagen almacenada del empleado
                    String storedImagePath = UPLOAD_DIR + emp.getImagen().split("/")[2];
                    String storedBase64Image = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(storedImagePath)));

                    // Comparar imágenes con Face++
                    String compareResult = faceRecognitionService.compareFaces(base64Image, storedBase64Image);
                    logger.info("Resultado de la comparación de reconocimiento facial: {}", compareResult);

                    // Analizar el resultado de la comparación (parsear el JSON)
                    JSONObject json = new JSONObject(compareResult);

                    if (json.has("confidence")) {
                        double confidence = json.getDouble("confidence");

                        if (confidence >= 75.0) { // Umbral de confianza
                            logger.info("Reconocimiento facial exitoso. Confianza: {}", confidence);
                        } else {
                            logger.warn("Reconocimiento facial fallido. Confianza: {}", confidence);
                            model.addAttribute("mensaje", "Reconocimiento facial fallido.");
                            return "entrada_salida";
                        }
                    } else {
                        logger.error("Error en la respuesta de reconocimiento facial. Detalles: {}", json.toString());
                        model.addAttribute("mensaje", "Error en la respuesta de reconocimiento facial.");
                        return "entrada_salida";
                    }
                }

                // Agregar el nuevo registro a la lista de registros del empleado
                emp.agregarRegistro(nuevoRegistro);

                // Guardar el empleado con el nuevo registro
                empleadoService.saveEmpleado(emp);
                logger.info("Registro de entrada/salida guardado correctamente para el empleado: {}", emp);

                // Preparar datos para la vista
                model.addAttribute("empleado", emp);
                model.addAttribute("horariosAsignados",
                        emp.getHorariosAsignados().stream()
                                .map(h -> "Ingreso: " + h.getIngreso() + ", Salida: " + h.getSalida())
                                .collect(Collectors.toList()));
                model.addAttribute("diasAsignados", emp.getDiasAsignados());

                // Generar el calendario de asistencia
                Map<String, List<Map.Entry<String, String>>> calendario = generarCalendarioAsistencia(emp);
                model.addAttribute("calendario", calendario);

                // Redirigir a la página de información del empleado
                return "redirect:/empleados/index?id=" + emp.getId();
            } catch (Exception e) {
                logger.error("Error en el procesamiento de la imagen o registro de entrada/salida: {}", e.getMessage(), e);
                model.addAttribute("mensaje", e.getMessage());
                return "entrada_salida";
            }
        } else {
            logger.warn("Empleado no encontrado con DNI: {}", dni);
            model.addAttribute("mensaje", "Empleado no encontrado.");
            return "entrada_salida";
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

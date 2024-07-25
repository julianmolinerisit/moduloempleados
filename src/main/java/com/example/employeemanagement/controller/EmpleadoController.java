package com.example.employeemanagement.controller;

import com.example.employeemanagement.model.Empleado;
import com.example.employeemanagement.model.RegistroEntradaSalida;
import com.example.employeemanagement.service.EmpleadoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public String createEmpleado(@ModelAttribute Empleado empleado) {
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
        return "entrada_salida"; // Asegúrate de que `entrada_salida.html` esté en `src/main/resources/templates/empleados/`
    }

    @PostMapping("/entrada-salida")
    public String registrarEntradaSalida(@RequestParam String dni, Model model) {
        Optional<Empleado> empleadoOpt = empleadoService.getEmpleadoByDni(dni);
        if (empleadoOpt.isPresent()) {
            Empleado emp = empleadoOpt.get();

            try {
                // Registrar entrada o salida usando el servicio
                empleadoService.registrarEntradaSalida(emp);

                // Preparar datos para la vista
                model.addAttribute("empleado", emp);
                model.addAttribute("horariosAsignados", emp.getHorariosAsignados().stream()
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
            model.addAttribute("horariosAsignados", empleado.getHorariosAsignados().stream()
                .map(h -> "Ingreso: " + h.getIngreso() + ", Salida: " + h.getSalida())
                .collect(Collectors.toList()));
            model.addAttribute("diasAsignados", empleado.getDiasAsignados());

            // Generar calendario de asistencia
            Map<String, List<Map.Entry<String, String>>> calendario = generarCalendarioAsistencia(empleado);
            model.addAttribute("calendario", calendario);

            return "index"; // Asegúrate de que `index.html` esté en `src/main/resources/templates/`
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

            calendario.computeIfAbsent(fecha, k -> new ArrayList<>()).add(new AbstractMap.SimpleEntry<>(registro.getTimestamp().toLocalTime().toString(), estado));
        }

        return calendario;
    }
}

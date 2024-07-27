package com.example.employeemanagement.service;

import com.example.employeemanagement.model.Empleado;
import com.example.employeemanagement.model.RegistroEntradaSalida;
import com.example.employeemanagement.repository.EmpleadoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class EmpleadoService {

    @Autowired
    private EmpleadoRepository empleadoRepository;

    public List<Empleado> getAllEmpleados() {
        return empleadoRepository.findAll();
    }

    public Optional<Empleado> getEmpleadoById(Long id) {
        return empleadoRepository.findById(id);
    }

    public Optional<Empleado> getEmpleadoByDni(String dni) {
        return empleadoRepository.findByDni(dni);
    }

    public void saveEmpleado(Empleado empleado) {
        empleadoRepository.save(empleado);
    }

    public void deleteEmpleado(Long id) {
        if (empleadoRepository.existsById(id)) {
            empleadoRepository.deleteById(id);
        } else {
            throw new IllegalArgumentException("Empleado no encontrado con ID: " + id);
        }
    }

    public void registrarEntradaSalida(Empleado empleado) {
        LocalDateTime ahora = LocalDateTime.now();
        List<RegistroEntradaSalida> registros = empleado.getRegistros();

        // Crear un nuevo registro de entrada o salida
        RegistroEntradaSalida nuevoRegistro = new RegistroEntradaSalida();
        nuevoRegistro.setTimestamp(ahora);

        if (registros.isEmpty()) {
            // Si no hay registros previos, asumir que el nuevo registro es una entrada
            nuevoRegistro.setEsEntrada(true);
        } else {
            // Obtener el último registro
            RegistroEntradaSalida ultimoRegistro = registros.get(registros.size() - 1);

            if (ultimoRegistro.isEsEntrada() && ultimoRegistro.getTimestamp().isBefore(ahora)) {
                // El último registro es una entrada y la nueva entrada es posterior
                nuevoRegistro.setEsEntrada(false); // Debe ser una salida
            } else {
                // Si el último registro es una salida o si la nueva entrada es anterior a la última entrada
                nuevoRegistro.setEsEntrada(true); // Debe ser una entrada
            }
        }

        empleado.agregarRegistro(nuevoRegistro);
        saveEmpleado(empleado);
    }

}

package com.example.employeemanagement.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

@Entity
public class Empleado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String apellido;
    private String dni;
    private Boolean activo;
    private String imagen; // Cambiar MultipartFile a String

    @ElementCollection
    @CollectionTable(name = "horarios_asignados", joinColumns = @JoinColumn(name = "empleado_id"))
    private List<Horario> horariosAsignados = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "dias_asignados", joinColumns = @JoinColumn(name = "empleado_id"))
    @Column(name = "dia")
    private List<String> diasAsignados = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "empleado_id")
    private List<RegistroEntradaSalida> registros = new ArrayList<>();

    // Getters y Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public List<Horario> getHorariosAsignados() {
        return horariosAsignados;
    }

    public void setHorariosAsignados(List<Horario> horariosAsignados) {
        this.horariosAsignados = horariosAsignados;
    }

    public List<String> getDiasAsignados() {
        return diasAsignados;
    }

    public void setDiasAsignados(List<String> diasAsignados) {
        this.diasAsignados = diasAsignados;
    }

    public List<RegistroEntradaSalida> getRegistros() {
        return registros;
    }

    public void setRegistros(List<RegistroEntradaSalida> registros) {
        this.registros = registros;
    }

    public void agregarRegistro(RegistroEntradaSalida registro) {
        this.registros.add(registro);
    }

    public String getImagen() {
        return imagen;
    }

    public void setImagen(String imagen) {
        this.imagen = imagen;
    }

    @Embeddable
    public static class Horario {
        private LocalTime ingreso;
        private LocalTime salida;

        public LocalTime getIngreso() {
            return ingreso;
        }

        public void setIngreso(LocalTime ingreso) {
            this.ingreso = ingreso;
        }

        public LocalTime getSalida() {
            return salida;
        }

        public void setSalida(LocalTime salida) {
            this.salida = salida;
        }
    }
}

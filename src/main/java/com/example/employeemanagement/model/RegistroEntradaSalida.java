package com.example.employeemanagement.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class RegistroEntradaSalida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;
    private boolean esEntrada;
    private String imagen; // Añadir este campo para almacenar la imagen
    private String retraso; // Añadir este campo para almacenar el retraso
    private long minutosRetraso;

    // Getters y Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isEsEntrada() {
        return esEntrada;
    }

    public void setEsEntrada(boolean esEntrada) {
        this.esEntrada = esEntrada;
    }

	public String getImagen() {
		return imagen;
	}

	public void setImagen(String imagen) {
		this.imagen = imagen;
	}

	public String getRetraso() {
		return retraso;
	}

	public void setRetraso(String retraso) {
		this.retraso = retraso;
	}

	public long getMinutosRetraso() {
		return minutosRetraso;
	}

	public void setMinutosRetraso(long minutosRetraso) {
		this.minutosRetraso = minutosRetraso;
	}
}

# MEMORIA TÉCNICA: SISTEMA DE COMUNICACIÓN MULTIPLATAFORMA TCP

---

**Asignatura:** Programación de Servicios y Procesos  
**Proyecto:** Chat TCP Multi-sala con Gestión de Canales y Filtros  
**Autor:** [Tu Nombre Aquí]  
**Fecha:** 28 de abril de 2026  

---

## ÍNDICE

1. [Introducción](#1-introducción)
2. [Arquitectura del Sistema](#2-arquitectura-del-sistema)
   - 2.1. Modelo Cliente-Servidor
   - 2.2. Multihilo y Concurrencia
3. [Implementación Técnica](#3-implementación-técnica)
   - 3.1. Protocolo de Comunicación (POJO)
   - 3.2. Gestión de Sockets
4. [Funcionalidades Principales](#4-funcionalidades-principales)
   - 4.1. Interfaz de Usuario Avanzada (JavaFX)
   - 4.2. Salas, Privados y Canales
5. [Extras y Valor Añadido](#5-extras-y-valor-añadido)
   - 5.1. Sistema Inteligente de Filtros (No Leídos)
   - 5.2. Control de Aforo Dinámico (Línea de Comandos)
   - 5.3. Despliegue Mediante Fat JAR (Maven Shade)
6. [Guía de Ejecución y Validación](#6-guía-de-ejecución-y-validación)
7. [Conclusión](#7-conclusión)

---

## 1. INTRODUCCIÓN
El presente trabajo consiste en el diseño y desarrollo de una aplicación de mensajería instantánea basada en sockets TCP. El sistema permite la comunicación en tiempo real entre múltiples usuarios bajo un entorno seguro y organizado. Se ha puesto especial énfasis en la **usabilidad**, implementando un sistema de filtrado de conversaciones y optimizando el despliegue mediante ejecutables independientes.

---

## 2. ARQUITECTURA DEL SISTEMA

### 2.1. Modelo Cliente-Servidor
El sistema utiliza una topología de estrella donde el **Servidor** actúa como nodo central (Hub), gestionando el enrutamiento de mensajes, el control de presencia y la validación de conexiones. El **Cliente** es una aplicación rica en interfaz que gestiona la visualización y la interacción del usuario.

### 2.2. Multihilo y Concurrencia
Para garantizar que el servidor no se bloquee mientras espera nuevos usuarios o procesa mensajes pesados, se ha implementado una arquitectura multihilo:
*   **Hilo Aceptor:** Escucha permanentemente en el puerto 55555.
*   **Hilos de Cliente (Worker Threads):** Por cada usuario conectado, el servidor genera un hilo dedicado (`HiloServidorChat`) que gestiona su flujo de entrada/salida de forma independiente, permitiendo la simultaneidad real.

---

## 3. IMPLEMENTACIÓN TÉCNICA

### 3.1. Protocolo de Comunicación (POJO)
En lugar de enviar texto plano, se ha definido una clase serializable llamada `DatosMensaje`. Esto permite enviar objetos complejos que contienen:
*   **Tipo de mensaje:** LOGIN, MENSAJE_PRIVADO, MENSAJE_CANAL, etc.
*   **Metadatos:** Timestamp para la hora, Remitente y Destinatario.
*   **Contenido:** El cuerpo del mensaje o listas de miembros para la creación de canales.

### 3.2. Gestión de Sockets
Se utilizan `ObjectOutputStream` y `ObjectInputStream` sobre los sockets TCP para permitir el paso de objetos Java de forma nativa, asegurando que la información llegue estructurada y sin errores de parseo.

---

## 4. FUNCIONALIDADES PRINCIPALES

### 4.1. Interfaz de Usuario Avanzada (JavaFX)
La interfaz ha sido diseñada siguiendo principios modernos, utilizando CSS para el estilizado de burbujas de chat, botones redondeados y efectos visuales de interactividad.

> **[inserta captura aquí: Captura de la ventana principal del chat con varias conversaciones abiertas y el diseño de burbujas]**

### 4.2. Salas, Privados y Canales
El sistema permite al usuario elegir con quién hablar:
*   **Sala General:** Mensajes visibles para todos los conectados.
*   **Privados:** Conversaciones uno a uno cifradas por el protocolo.
*   **Canales:** Grupos cerrados creados dinámicamente por los usuarios.

---

## 5. EXTRAS Y VALOR AÑADIDO

### 5.1. Sistema Inteligente de Filtros (No Leídos)
Se ha desarrollado un motor de estados para las conversaciones. El cliente monitoriza en tiempo real qué mensajes llegan mientras el usuario está en otra sala.
*   **Filtro Dinámico:** Al seleccionar la carpeta "No Leídos", el lateral se limpia instantáneamente mostrando solo lo pendiente.
*   **Limpieza Automática:** Al abrir un chat pendiente, el sistema refresca la UI y lo elimina de la lista de pendientes de forma reactiva e inmediata.
*   **Notificación Visual:** Se ha incluido un icono de campana (🔔) para resaltar mensajes nuevos en la lista general.

> **[inserta captura aquí: Captura del lateral izquierdo con el filtro de 'No leídos' activo y el indicador 🔔]**

### 5.2. Control de Aforo Dinámico (Línea de Comandos)
El servidor permite configurar el límite de usuarios sin modificar el código. Mediante el parámetro `-usuariosMaximos`, el administrador define el aforo al arrancar el proceso.
*   Ejemplo: `java -jar servidor.jar -usuariosMaximos 5`

> **[inserta captura aquí: Captura de la terminal ejecutando el servidor con el parámetro de usuarios máximos]**

### 5.3. Despliegue Mediante Fat JAR (Maven Shade)
Se ha configurado el `maven-shade-plugin` para empaquetar todas las dependencias (JavaFX, Gson) en archivos únicos.
*   **cliente.jar:** Ejecutable de la aplicación de usuario.
*   **servidor.jar:** Ejecutable del nodo central.

> **[inserta captura aquí: Captura de la carpeta 'target' mostrando ambos archivos ejecutables .jar]**

---

## 6. GUÍA DE EJECUCIÓN Y VALIDACIÓN
Para validar el sistema, se recomienda realizar el siguiente protocolo:
1.  **Arranque:** Iniciar el servidor con un límite establecido.
2.  **Conexión:** Abrir múltiples clientes y verificar el login anónimo.
3.  **Comunicación:** Enviar mensajes y comprobar el funcionamiento de las carpetas de "No leídos".
4.  **Finalización:** Cerrar clientes y observar la actualización de la lista de usuarios en tiempo real.

---

## 7. CONCLUSIÓN
El proyecto cumple satisfactoriamente con los requisitos académicos, aportando mejoras significativas en la experiencia de usuario y robustez técnica. La arquitectura implementada permite una gran escalabilidad para futuras actualizaciones.

package org.kaaproject.client.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;

import org.apache.commons.lang3.RandomUtils;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Main {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String SERVER_URI = "tcp://10.2.3.101:30720";
  private static final String CLIENT_ID = "mqtt-kp-client";

  private static final String TOPIC = "kp1/engine-ver-1/dcx_instance_1/epToken/json/";
  public static final MqttKpClient client = new MqttKpClient(SERVER_URI, CLIENT_ID);


  public static final int BASE_TEMPERATURE = 30;
  public static final int MAX_TEMPERATURE = 110;
  public static final int BASE_REVOLUTION = 500;
  public static final int MAX_PRESSURE_REVOLUTION = 2000;
  public static final int MAX_REVOLUTION = 3000;
  public static final int MIN_STOP_REVOLUTION = 60;
  public static final int BASE_PRESSURE = 2;
  public static final int MAX_PRESSURE = 6;

  public static final int PERIOD_MILLIS = 500;
  public static final double PERIOS_COEF = PERIOD_MILLIS / 1000.0;

  private static boolean working = false;
  private static double currentRevolution = 0;
  private static double currentPressure = 0;
  private static double currentTemperature = BASE_TEMPERATURE;


  @SneakyThrows
  public static void main(String[] args) {
    while (true) {

      for (int i = 0; i < Math.round(10.0 / PERIOS_COEF); i++) {
        generateAndSendMessage();
      }

      System.out.println("=======ON=====");

      working = true;
      for (int i = 0; i < Math.round(60.0 / PERIOS_COEF); i++) {
        generateAndSendMessage();
      }

      System.out.println("=======OFF=====");

      working = false;
      for (int i = 0; i < Math.round(30.0 / PERIOS_COEF); i++) {
        generateAndSendMessage();
      }
  }

  }

  @SneakyThrows
  private static void generateAndSendMessage() {
    client.publish(TOPIC, generateMqttMessage());
//    generateMqttMessage();
    System.out.println("----------------------------------------");
    System.out.println("REVOLUTION = " + currentRevolution);
    System.out.println("Oil pressure = " + currentPressure);
    System.out.println("Temperature = " + currentTemperature);
    sleepForMillis(PERIOD_MILLIS);
  }

  @SneakyThrows
  private static MqttMessage generateMqttMessage() {
    MqttMessage mqttMessage = new MqttMessage();
    mqttMessage.setId(RandomUtils.nextInt());
    mqttMessage.setQos(1);
    mqttMessage.setPayload(generatePayload("/payload.json"));
    return mqttMessage;
  }

  @SneakyThrows
  private static byte[] generatePayload(String filename) {
    Path path = Paths.get(Main.class.getResource(filename).toURI());
    String unformattedPayload = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    double rev = generateRevolutions();
    double temp = genTemperature();
    double oil = genOil();
    String formattedPayload = String.format(unformattedPayload, OffsetDateTime.now().toString(), temp, oil, rev);
    JsonNode node = OBJECT_MAPPER.readTree(formattedPayload);
    return OBJECT_MAPPER.writeValueAsBytes(node);
  }

  private static double generateRevolutions() {
    if (working) {
      if (currentRevolution < MAX_REVOLUTION) {
        if (currentRevolution < BASE_REVOLUTION) {
          currentRevolution = BASE_REVOLUTION;
        }
        currentRevolution = currentRevolution + PERIOS_COEF * currentRevolution * ThreadLocalRandom.current().nextDouble(0.1, 0.2);
      } else {
        currentRevolution = ThreadLocalRandom.current().nextDouble(MAX_REVOLUTION - 9, MAX_REVOLUTION + 4);
      }
    } else {
      double revRandCoef = 10.1;
      double revSpeedReductionCoef = 0.4;
      double newValue = (MAX_REVOLUTION - currentRevolution + 10) * revSpeedReductionCoef + ThreadLocalRandom.current().nextDouble(-1, 1) * revRandCoef;
      currentRevolution -= PERIOS_COEF * newValue;
      currentRevolution = (currentRevolution < MIN_STOP_REVOLUTION ? 0 : currentRevolution);
    }
    if (currentRevolution > MAX_REVOLUTION) {
      currentRevolution = ThreadLocalRandom.current().nextDouble(MAX_REVOLUTION - 9, MAX_REVOLUTION + 4);
    }
    return currentRevolution;
  }

  private static double genOil() {
    double pressureCoef = 6.0 / 2000;
    double pressureRandomCoef = 0.06;
    currentPressure = currentRevolution * pressureCoef + PERIOS_COEF * (currentRevolution > 1 ? ThreadLocalRandom.current().nextDouble(-1, 1) * pressureRandomCoef : 0);
    if (currentPressure > MAX_PRESSURE) {
      currentPressure = MAX_PRESSURE + ThreadLocalRandom.current().nextDouble(-1, 1) * pressureRandomCoef;
    } else if (currentPressure < 0) {
      currentPressure = 0;
    }
    return currentPressure;
  }

  private static double genTemperature() {
    double temperatureRandomCoeff = 1.5;
    double tempCoeff = 7.0 / 3000;
    double addValue = (currentRevolution - 1.5 * BASE_REVOLUTION) * tempCoeff + ThreadLocalRandom.current().nextDouble(-0.2, 0.2) * temperatureRandomCoeff;
    currentTemperature += PERIOS_COEF * addValue;
    if (currentTemperature > MAX_TEMPERATURE) {
      currentTemperature = MAX_TEMPERATURE + ThreadLocalRandom.current().nextDouble(-0.5, 0.5) * temperatureRandomCoeff;
    } else if (currentTemperature < BASE_TEMPERATURE) {
      currentTemperature = ThreadLocalRandom.current().nextDouble(BASE_TEMPERATURE - 0.1, BASE_TEMPERATURE + 0.1);
    }
    return currentTemperature;
  }

  public static void sleepForSeconds(int seconds) {
    try {
      TimeUnit.SECONDS.sleep(seconds);
    } catch (InterruptedException e) {
      System.out.println("Interrupted when thread was sleep for timeout");
    }
  }

  public static void sleepForMillis(int millis) {
    try {
      TimeUnit.MILLISECONDS.sleep(millis);
    } catch (InterruptedException e) {
      System.out.println("Interrupted when thread was sleep for timeout");
    }
  }

}

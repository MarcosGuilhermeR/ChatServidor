/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.marcos.servico;

import com.marcos.entidade.Mensagem;
import com.marcos.entidade.Mensagem.Action;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author trabalho
 */
public class ServidorServico {

    private ServerSocket serverSocket;
    private Socket socket;

    //Lista dos usuarios conectados
    private Map<String, ObjectOutputStream> mapOnlines = new HashMap<String, ObjectOutputStream>();

    public ServidorServico() {
        try {
            serverSocket = new ServerSocket(5557);
            new Thread(new UDPThread()).start();
            while (true) {
                System.out.println("Aguardando conexao TCP...");
                socket = serverSocket.accept();
                System.out.println("Cliente conectado...");

                //Thread para atender o cliente
                new Thread(new threadAtenderCliente(socket)).start();
            }
        } catch (IOException ex) {
            Logger.getLogger(ServidorServico.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private class threadAtenderCliente implements Runnable {

        private ObjectOutputStream output; //Objeto de saída do cliente
        private ObjectInputStream input; //Objeto de entrada do cliente

        public threadAtenderCliente(Socket socket) {
            try {
                this.output = new ObjectOutputStream(socket.getOutputStream());
                this.input = new ObjectInputStream(socket.getInputStream());
            } catch (IOException ex) {
                Logger.getLogger(ServidorServico.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        @Override
        public void run() {
            Mensagem message = new Mensagem();
            try {
                while ((message = (Mensagem) input.readObject()) != null) {
                    Action action = message.getAction();

                    if (action.equals(Action.CONNECT)) { //Cliente se conectou

                        boolean isConnect = connect(message, output);
                        if (isConnect) {
                            System.out.println(message.getName() + " Conectou...");

                            /*Insere nome/objSaidaCliente na lista de clientes
                            onlines*/
                            mapOnlines.put(message.getName(), output);

                            message.setAction(Action.SEND_ALL);
                            message.setText(message.getName() + " entrou na sala");

                            /*Informa todos participantes que um novo usuário 
                              entrou*/
                            sendAll(message);
                        }
                    } else if (action.equals(Action.DISCONECT)) {
                        //Cliente desconectou
                        disconnect(message, output);
                        return;//Forcar saída do while
                    } else if (action.equals(Action.SEND_ONE)) {
                        //Envia mensagem a um cliente
                        sendOne(message, output);
                    } else if (action.equals(Action.SEND_ALL)) {
                        //Envia mensagem a todos clientes
                        message.setText(message.getName() + " disse: " + message.getText());
                        sendAll(message);
                    }
                }
            } catch (IOException ex) {
                System.out.println("Excecao: " + ex);
                disconnect(message, output);

            } catch (ClassNotFoundException ex) {
                Logger.getLogger(ServidorServico.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        //Metodo para conexao do cliente
        private boolean connect(Mensagem message, ObjectOutputStream output) {
            for (Map.Entry<String, ObjectOutputStream> kv : mapOnlines.entrySet()) {
                if (kv.getKey().equalsIgnoreCase(message.getName())) {
                    message.setText("NO");
                    sendOne(message, output);
                    return false;
                }
            }

            message.setText("YES");
            sendOne(message, output);
            return true;
        }

        //Metodo para desconexão do cliente
        public void disconnect(Mensagem message, ObjectOutputStream output) {
            mapOnlines.remove(message.getName());
            message.setText(message.getName() + " saiu da sala");
            message.setAction(Action.SEND_ALL);
            sendAll(message);
            System.out.println(message.getName() + " Saiu da sala.");
        }

        //Metodo para enviar mensagem a um cliente
        private void sendOne(Mensagem message, ObjectOutputStream output) {
            try {
                output.writeObject(message);
            } catch (IOException ex) {
                Logger.getLogger(ServidorServico.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        //Metodo para enviar mensagem a todos os Clientes
        private void sendAll(Mensagem message) {
            for (Map.Entry<String, ObjectOutputStream> kv : mapOnlines.entrySet()) {
                if (!kv.getKey().equals(message.getName())) {
                    try {
                        kv.getValue().writeObject(message);
                    } catch (IOException ex) {
                        Logger.getLogger(ServidorServico.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
    }

    //Thread Para Receber Mensagens UDPs
    public class UDPThread implements Runnable {

        InetAddress IPAddress; //Armazena endereco do Servidor    

        @Override
        public void run() {

            DatagramSocket receptorSocket = null;
            try {
                IPAddress = InetAddress.getByName("239.255.255.255");
                receptorSocket = new DatagramSocket(5556);
                //receptorSocket.joinGroup(IPAddress);
            } catch (SocketException ex) {
                Logger.getLogger(UDPThread.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(ServidorServico.class.getName()).log(Level.SEVERE, null, ex);
            }

            while (true) {
                System.out.println("Aguardando Requisição UDP");
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                try {
                    receptorSocket.receive(receivePacket);
                } catch (IOException ex) {
                    Logger.getLogger(UDPThread.class.getName()).log(Level.SEVERE, null, ex);
                }

                String sentence = new String(receivePacket.getData());
                System.out.println("Mensagem " + sentence + " recebida de: " + receivePacket.getAddress());

                byte[] sendData = new byte[1024];

                sendData = sentence.getBytes();
                int porta = 5555; //Porta de conexao do grupo multicast
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, porta);
                DatagramSocket clientSocket;

                sentence = sentence.trim();
                try {
                    Thread.currentThread().sleep(1000); // 1 segundo
                } catch (InterruptedException ex) {
                    Logger.getLogger(ServidorServico.class.getName()).log(Level.SEVERE, null, ex);
                }

                if (sentence.equals("status")) {
                    try {
                        String sentenceOK = "ok";

                        byte[] sendData2 = new byte[1024];

                        sendData2 = sentenceOK.getBytes();

                        sendPacket = new DatagramPacket(sendData2, sendData2.length, IPAddress, porta);

                        clientSocket = new DatagramSocket();
                        clientSocket.send(sendPacket);
                        System.out.println("Mensagem Enviada");
                    } catch (IOException ex) {
                        Logger.getLogger(ServidorServico.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                try {
                    clientSocket = new DatagramSocket();
                    clientSocket.send(sendPacket);
                    System.out.println("Mensagem Enviada");
                } catch (IOException ex) {
                    Logger.getLogger(ServidorServico.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        }
    }
}

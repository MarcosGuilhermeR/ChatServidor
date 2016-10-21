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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author trabalho
 */
public class ServidorService {

    private ServerSocket serverSocket;
    private Socket socket;

    //Lista dos usuários conectados
    private Map<String, ObjectOutputStream> mapOnlines = new HashMap<String, ObjectOutputStream>();

    public ServidorService() {
        try {
            serverSocket = new ServerSocket(5557);

            while (true) {
                System.out.println("Aguardando conexão...");
                socket = serverSocket.accept();
                System.out.println("Cliente conectado...");

                //Thread para atender o cliente
                new Thread(new threadAtenderCliente(socket)).start();
            }
        } catch (IOException ex) {
            Logger.getLogger(ServidorService.class.getName()).log(Level.SEVERE, null, ex);
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
                Logger.getLogger(ServidorService.class.getName()).log(Level.SEVERE, null, ex);
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
                        return;//Forçar saída do while
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
                System.out.println("Exceção: " + ex);
                disconnect(message, output);

            } catch (ClassNotFoundException ex) {
                Logger.getLogger(ServidorService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        //Método para conexao do cliente
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

        //Método para desconexão do cliente
        public void disconnect(Mensagem message, ObjectOutputStream output) {
            mapOnlines.remove(message.getName());
            message.setText(message.getName() + " saiu da sala");
            message.setAction(Action.SEND_ALL);
            sendAll(message);
            System.out.println(message.getName() + " Saiu da sala.");
        }

        //Método para enviar mensagem a um cliente
        private void sendOne(Mensagem message, ObjectOutputStream output) {
            try {
                output.writeObject(message);
            } catch (IOException ex) {
                Logger.getLogger(ServidorService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        //Método para enviar mensagem a todos os Clientes
        private void sendAll(Mensagem message) {
            for (Map.Entry<String, ObjectOutputStream> kv : mapOnlines.entrySet()) {
                if (!kv.getKey().equals(message.getName())) {
                    try {
                        kv.getValue().writeObject(message);
                    } catch (IOException ex) {
                        Logger.getLogger(ServidorService.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
    }
}

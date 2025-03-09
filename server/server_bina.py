from flask import Flask, request
import webbrowser
import re
import logging
import configparser
import os


app = Flask(__name__)

current_directory = os.path.dirname(os.path.abspath(__file__))
logFileName = (os.path.join(current_directory, 'server.log'))    
    
# Configuração de logs
logging.basicConfig(filename=logFileName, level=logging.INFO, 
                    format='%(asctime)s - %(levelname)s - %(message)s')

# Função de validação de número de telefone
def is_valid_phone_number(phone_number):
    # Verifica se o número tem apenas dígitos e entre 7 e 15 dígitos
    return bool(re.fullmatch(r'\d{7,15}', phone_number))

# Função para carregar a URL do arquivo de propriedades
def get_url_from_properties(phone_number):
    config = configparser.ConfigParser()
    
    current_directory = os.path.dirname(os.path.abspath(__file__))
    config.read(os.path.join(current_directory, 'config.properties'))    

    # Busca a URL no arquivo de propriedades
    try:
        url_template = config.get('Settings', 'url_template')
        if '{number}' in url_template:
            # Substitui {number} pelo número de telefone
            return url_template.replace('{number}', phone_number)
        else:
            logging.error('A chave {number} não foi encontrada no template da URL.')
            return None
    except (configparser.NoOptionError, configparser.NoSectionError) as e:
        logging.error(f'Erro ao ler o arquivo de propriedades: {e}')
        return None

@app.route('/')
def handle_call():
    # Pega o número de telefone da query string (parâmetro 'number')
    phone_number = request.args.get('number')

    if phone_number:
        # Valida o número de telefone
        if is_valid_phone_number(phone_number):
            # Busca a URL a partir do arquivo de propriedades
            url = get_url_from_properties(phone_number)
            if url:
                # Exibe no terminal e registra no log o número recebido
                log_message = f"Recebido número válido: {phone_number}. URL gerada: {url}"
                print(log_message)
                logging.info(log_message)

                # Abre a URL no navegador
                webbrowser.open(url)
                
                return f"Número {phone_number} recebido e URL aberta no navegador.", 200
            else:
                logging.error(f'Falha ao gerar a URL para o número {phone_number}')
                return "Erro ao gerar a URL.", 500
        else:
            logging.warning(f"Número inválido: {phone_number}")
            return "Número de telefone inválido.", 400
    else:
        logging.warning("Número não fornecido.")
        return "Número não fornecido.", 400

if __name__ == '__main__':
    # Executa o servidor Flask na porta 5000
    app.run(host='0.0.0.0', port=5000)

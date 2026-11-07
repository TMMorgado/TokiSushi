(function () {
  // ----- Passo 1: arrastar / escolher ficheiros -----
  var zona = document.getElementById('zona');
  var input = document.getElementById('ficheiros');
  var lista = document.getElementById('lista');
  var enviar = document.getElementById('enviar');
  var contador = document.getElementById('contador');
  var form = document.getElementById('form-upload');

  if (zona && input && form) {
    var ficheiros = [];

    function tamanho(b) { return b > 1048576 ? (b / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(b / 1024)) + ' KB'; }

    function sincronizar() {
      var dt = new DataTransfer();
      ficheiros.forEach(function (f) { dt.items.add(f); });
      input.files = dt.files;
      lista.innerHTML = '';
      ficheiros.forEach(function (f, i) {
        var li = document.createElement('li');
        li.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/></svg>';
        var nome = document.createElement('span'); nome.className = 'nome'; nome.textContent = f.name;
        var tam = document.createElement('span'); tam.className = 'tam'; tam.textContent = tamanho(f.size);
        var rem = document.createElement('button'); rem.type = 'button'; rem.className = 'remover';
        rem.setAttribute('aria-label', 'Remover ' + f.name); rem.textContent = '×';
        rem.addEventListener('click', function () { ficheiros.splice(i, 1); sincronizar(); });
        li.append(nome, tam, rem); lista.appendChild(li);
      });
      enviar.disabled = ficheiros.length === 0;
      contador.textContent = ficheiros.length === 0 ? '' : ficheiros.length + (ficheiros.length === 1 ? ' fatura pronta' : ' faturas prontas');
    }

    function adicionar(novos) {
      Array.prototype.forEach.call(novos, function (f) {
        var pdf = f.type === 'application/pdf' || /\.pdf$/i.test(f.name);
        var repetido = ficheiros.some(function (x) { return x.name === f.name && x.size === f.size; });
        if (pdf && !repetido) ficheiros.push(f);
      });
      sincronizar();
    }

    input.addEventListener('change', function () { adicionar(input.files); });
    ['dragenter', 'dragover'].forEach(function (ev) {
      zona.addEventListener(ev, function (e) { e.preventDefault(); zona.classList.add('sobre'); });
    });
    ['dragleave', 'drop'].forEach(function (ev) {
      zona.addEventListener(ev, function (e) { e.preventDefault(); zona.classList.remove('sobre'); });
    });
    zona.addEventListener('drop', function (e) { adicionar(e.dataTransfer.files); });
    form.addEventListener('submit', function () { document.body.classList.add('a-processar'); });
  }

  // ----- Passo 2: ampliar a fatura -----
  var dialogo = document.getElementById('ampliar');
  if (dialogo) {
    var titulo = dialogo.querySelector('[data-titulo]');
    var folhas = dialogo.querySelector('.folhas');
    document.querySelectorAll('[data-abrir]').forEach(function (botao) {
      botao.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); botao.click(); }
      });
      botao.addEventListener('click', function () {
        folhas.innerHTML = '';
        botao.querySelectorAll('img.pagina').forEach(function (img) {
          var c = document.createElement('img'); c.src = img.src; c.alt = 'Página da fatura'; folhas.appendChild(c);
        });
        titulo.textContent = botao.getAttribute('data-nome') || 'Fatura';
        dialogo.showModal();
      });
    });
    dialogo.querySelector('.fechar').addEventListener('click', function () { dialogo.close(); });
    dialogo.addEventListener('click', function (e) { if (e.target === dialogo) dialogo.close(); });
  }

  // ----- Passo 2: filtrar "Todas" / "Para rever" -----
  document.querySelectorAll('[data-filtro]').forEach(function (chip) {
    chip.addEventListener('click', function () {
      var f = chip.getAttribute('data-filtro');
      document.querySelectorAll('[data-filtro]').forEach(function (c) { c.classList.toggle('ativo', c === chip); });
      document.querySelectorAll('.fatura').forEach(function (card) {
        card.classList.toggle('oculta', f === 'rever' && card.getAttribute('data-estado') !== 'rever');
      });
    });
  });
})();

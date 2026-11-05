import { test } from 'node:test';
import assert from 'node:assert/strict';
import { devComputerHost, devServerCandidates, normalizeServerUrl } from '../src/serverUrl.js';

test('sin esquema se asume http y se conserva el puerto', () => {
  assert.equal(normalizeServerUrl('192.168.1.20:8080'), 'http://192.168.1.20:8080');
});

test('se quitan espacios, barra final, consulta y ruta de la app web', () => {
  assert.equal(normalizeServerUrl('  http://192.168.1.20:8080/  '), 'http://192.168.1.20:8080');
  assert.equal(normalizeServerUrl('http://192.168.1.20:8080/#/inicio'), 'http://192.168.1.20:8080');
  assert.equal(normalizeServerUrl('http://192.168.1.20:8080/?apertura'), 'http://192.168.1.20:8080');
});

test('un túnel con HTTPS se respeta tal cual', () => {
  assert.equal(normalizeServerUrl('https://Flux-Demo.trycloudflare.com'), 'https://flux-demo.trycloudflare.com');
});

test('una ruta bajo el servidor se conserva', () => {
  assert.equal(normalizeServerUrl('http://10.0.0.5:5500/frontend/'), 'http://10.0.0.5:5500/frontend');
});

test('lo que no es una dirección http devuelve null', () => {
  for (const input of ['', '   ', null, undefined, 'ftp://servidor', 'http://', 'http://con espacio:8080']) {
    assert.equal(normalizeServerUrl(input), null, `«${input}» debería ser null`);
  }
});

test('la IP del computador sale de la dirección del servidor de Expo', () => {
  assert.equal(devComputerHost('192.168.80.44:8090'), '192.168.80.44');
  assert.equal(devComputerHost('192.168.80.44'), '192.168.80.44');
});

test('sin servidor de Expo (APK) o con un túnel de Expo no hay computador donde buscar', () => {
  for (const hostUri of [undefined, null, '', 'abc-anonymous-8090.exp.direct']) {
    assert.equal(devComputerHost(hostUri), null, `«${hostUri}» debería ser null`);
  }
});

test('si la IP del computador cambió, se busca en la nueva empezando por el puerto guardado', () => {
  assert.deepEqual(devServerCandidates('http://192.168.20.84:8081', '192.168.80.44'), [
    'http://192.168.80.44:8081',
    'http://192.168.80.44:8080',
  ]);
});

test('sin dirección guardada se prueban los puertos habituales del backend', () => {
  assert.deepEqual(devServerCandidates(null, '192.168.80.44'), [
    'http://192.168.80.44:8080',
    'http://192.168.80.44:8081',
  ]);
});

test('no se busca si la dirección ya apunta a ese computador, si es HTTPS o si no hay servidor de Expo', () => {
  assert.deepEqual(devServerCandidates('http://192.168.80.44:8081', '192.168.80.44'), []);
  assert.deepEqual(devServerCandidates('https://flux-demo.trycloudflare.com', '192.168.80.44'), []);
  assert.deepEqual(devServerCandidates('http://192.168.20.84:8081', null), []);
});

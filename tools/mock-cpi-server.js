// Throwaway local mock of Elavon Cloud Payments Interface (CPI) v2.0, for testing the
// card-present terminal integration end-to-end without real Elavon credentials or a
// physical Ingenico Desk/3500. Not part of the shipped app — delete once real UAT
// credentials/sandbox access exist.
//
// Run:   node tools/mock-cpi-server.js
// Then in backend/src/main/resources/application-local.yml, point CPI at it:
//   elavon.cpi.base-url: http://localhost:4010
//   elavon.cpi.client-id: mock
//   elavon.cpi.client-secret: mock
//
// Response shapes below are copied from the real CPI OpenAPI spec's own examples
// (downloaded from developer.elavon.com), not guessed.

const http = require('http')
const crypto = require('crypto')

const PORT = 4010

function readBody(req) {
    return new Promise(resolve => {
        let data = ''
        req.on('data', chunk => { data += chunk })
        req.on('end', () => resolve(data))
    })
}

function sendJson(res, status, body) {
    res.writeHead(status, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify(body))
}

function fakeId(prefix) {
    return prefix + '-' + crypto.randomBytes(6).toString('hex')
}

function approvedTransaction(message) {
    return {
        referenceNumber: message.referenceNumber,
        transType: message.transType,
        transAmount: message.transAmount,
        identifiers: message.identifiers ?? null,
        cashierId: message.cashierId ?? null,
        safetyFields: {
            tokenization: {
                token: 'ID:' + fakeId('tok')
            }
        },
        card: {
            maskedPAN: 'XXXXXXXXXXXX1234',
            expirationMonth: '12',
            expirationYear: '27',
            tenderType: 'CREDIT'
        },
        response: {
            authorizationCode: '118769',
            responseCode: '0000',
            responseText: 'APPROVED',
            hostResponseCode: '00',
            hostResponseText: 'Approved'
        }
    }
}

function responseEnvelope(requestEnvelope, transaction) {
    return {
        responseChannelType: 'SYNCHRONOUS',
        responseChannel: { responsePayloadFormat: 'DEVICEMESSAGE' },
        messageId: requestEnvelope.messageId,
        messageType: requestEnvelope.messageType,
        message: transaction
    }
}

const server = http.createServer(async (req, res) => {
    const body = await readBody(req)
    console.log(`[mock-cpi] ${req.method} ${req.url}`)
    if (body) {
        console.log(body)
    }

    // POST /credentials/token — Basic auth exchange, accepts anything
    if (req.method === 'POST' && req.url === '/credentials/token') {
        return sendJson(res, 200, {
            client_id: 'mock',
            access_token: fakeId('token'),
            token_type: 'bearer',
            expires_in: 900,
            jti: fakeId('jti')
        })
    }

    // POST /devices — pair a device, echoes back a fake CPI deviceId
    if (req.method === 'POST' && req.url === '/devices') {
        const data = JSON.parse(body)
        return sendJson(res, 201, {
            deviceIdentifiers: {
                deviceId: fakeId('device'),
                deviceFriendlyName: data.deviceFriendlyName,
                location: data.location ?? null,
                serialNumber: fakeId('serial')
            },
            deviceManufacturer: 'I',
            deviceModel: 'Desk/3500',
            deviceType: 'PINPAD',
            pairedStatus: 'PAIRED'
        })
    }

    // DELETE /devices/{id} — unpair
    if (req.method === 'DELETE' && /^\/devices\/[^/]+$/.test(req.url)) {
        res.writeHead(204)
        return res.end()
    }

    // POST /devices/{id}/message — the actual transaction endpoint (AUTH, REFUND, etc.)
    if (req.method === 'POST' && /^\/devices\/[^/]+\/message$/.test(req.url)) {
        const envelope = JSON.parse(body)
        return sendJson(res, 201, responseEnvelope(envelope, approvedTransaction(envelope.message)))
    }

    // POST /gateways/message — PRIORAUTHCOMPLETION, VOIDSALE, etc.
    if (req.method === 'POST' && req.url === '/gateways/message') {
        const envelope = JSON.parse(body)
        return sendJson(res, 201, responseEnvelope(envelope, approvedTransaction(envelope.message)))
    }

    sendJson(res, 404, { error: 'not found in mock CPI server' })
})

server.listen(PORT, () => {
    console.log(`Mock Elavon CPI server listening on http://localhost:${PORT}`)
})

import Foundation

struct MultipartFormDataBuilder {
    let boundary = "Boundary-\(UUID().uuidString)"
    private var data = Data()

    mutating func append(field name: String, value: String) {
        data.appendString("--\(boundary)\r\n")
        data.appendString("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n")
        data.appendString(value)
        data.appendString("\r\n")
    }

    mutating func append(file fieldName: String, filename: String, mimeType: String, data fileData: Data) {
        data.appendString("--\(boundary)\r\n")
        data.appendString("Content-Disposition: form-data; name=\"\(fieldName)\"; filename=\"\(filename)\"\r\n")
        data.appendString("Content-Type: \(mimeType)\r\n\r\n")
        data.append(fileData)
        data.appendString("\r\n")
    }

    mutating func finalize() -> (Data, String) {
        data.appendString("--\(boundary)--\r\n")
        return (data, "multipart/form-data; boundary=\(boundary)")
    }
}

private extension Data {
    mutating func appendString(_ string: String) {
        append(Data(string.utf8))
    }
}


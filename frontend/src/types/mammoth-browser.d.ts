declare module 'mammoth/mammoth.browser' {
  export interface MammothConvertInput {
    arrayBuffer: ArrayBuffer
  }

  export interface MammothMessage {
    type?: string
    message?: string
  }

  export interface MammothConvertResult {
    value: string
    messages: MammothMessage[]
  }

  const mammoth: {
    convertToHtml(input: MammothConvertInput): Promise<MammothConvertResult>
  }

  export default mammoth
}

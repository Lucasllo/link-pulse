{{/*
Nome do chart (ou nameOverride), até 63 caracteres.
*/}}
{{- define "link-pulse.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Nome completo dos recursos. O release link-pulse dá Service link-pulse (contrato do ServiceMonitor,
do LINKPULSE_SELF_HOSTS e do smoke).
*/}}
{{- define "link-pulse.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Nome e versão do chart para o label helm.sh/chart.
*/}}
{{- define "link-pulse.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Labels comuns.
*/}}
{{- define "link-pulse.labels" -}}
helm.sh/chart: {{ include "link-pulse.chart" . }}
{{ include "link-pulse.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Labels do seletor (estáveis entre upgrades).
*/}}
{{- define "link-pulse.selectorLabels" -}}
app.kubernetes.io/name: {{ include "link-pulse.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Secret com SPRING_DATASOURCE_PASSWORD e LINKPULSE_IP_HASH_KEY: o do chart (secret.create) ou um
existente, criado fora do Helm (values-prod).
*/}}
{{- define "link-pulse.secretName" -}}
{{- if .Values.secret.create }}
{{- include "link-pulse.fullname" . }}
{{- else }}
{{- required "secret.existingSecret é obrigatório quando secret.create=false" .Values.secret.existingSecret }}
{{- end }}
{{- end }}

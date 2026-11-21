param([string]$Model = "qwen3:8b")

$body = @{
    model    = $Model
    stream   = $false
    messages = @(@{ role = "user"; content = "Any trains from Chennai to Bengaluru on 2026-11-22?" })
    tools    = @(@{
        type     = "function"
        function = @{
            name        = "search_trains"
            description = "Search trains between two cities on a date"
            parameters  = @{
                type       = "object"
                properties = @{
                    origin      = @{ type = "string" }
                    destination = @{ type = "string" }
                    date        = @{ type = "string"; description = "YYYY-MM-DD" }
                }
                required   = @("origin", "destination", "date")
            }
        }
    })
} | ConvertTo-Json -Depth 10

$r = Invoke-RestMethod -Uri "http://localhost:11434/api/chat" -Method Post `
        -ContentType "application/json" -Body $body

if ($r.message.tool_calls) {
    $r.message.tool_calls | ConvertTo-Json -Depth 10
} else {
    "No tool call. Model replied: " + $r.message.content
}